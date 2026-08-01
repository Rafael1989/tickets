import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { FareRuleRequest, FareRuleResponse } from '../../../core/models/fare-rule.model';
import { FareRuleService } from '../../../core/services/fare-rule.service';
import { NotificationService } from '../../../core/services/notification.service';

const CSV_HEADER = ['seatclass', 'validfrom', 'validto', 'surchargerate'];

interface PreviewRow {
  line: number;
  seatClass: string;
  validFrom: string;
  validTo: string;
  surchargeRate: string;
  error: string | null;
}

/**
 * A "fare matrix" here is simply the set of FareRule rows for one route: a
 * seasonal surcharge/discount rate per seat class and date window, stacked
 * on top of the schedule's existing demand-based pricing (see
 * PricingServiceImpl.calculateFareRuleAdjustment on the backend). Bulk
 * loading parses/validates the CSV entirely client-side and only ever POSTs
 * a validated JSON array - there's no server-side file upload endpoint.
 */
@Component({
  selector: 'tw-fare-matrix',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './fare-matrix.component.html',
  styleUrl: './fare-matrix.component.scss',
})
export class FareMatrixComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly fareRuleService = inject(FareRuleService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly routeId = input.required<number>();

  readonly fareRules = signal<FareRuleResponse[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly uploading = signal(false);
  readonly previewRows = signal<PreviewRow[]>([]);
  readonly previewFileName = signal<string | null>(null);

  readonly hasPreviewErrors = computed(() => this.previewRows().some((row) => row.error !== null));

  readonly ruleForm = this.fb.nonNullable.group({
    seatClass: ['economy', Validators.required],
    validFrom: ['', Validators.required],
    validTo: ['', Validators.required],
    surchargeRate: [0, [Validators.required, Validators.min(-1), Validators.max(5)]],
  });

  ngOnInit(): void {
    this.loading.set(true);
    this.fareRuleService
      .listFareRulesForRoute(this.routeId())
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (rules) => this.fareRules.set(rules) });
  }

  createFareRule(): void {
    if (this.ruleForm.invalid || this.saving()) {
      return;
    }
    const value = this.ruleForm.getRawValue();

    this.saving.set(true);
    this.fareRuleService
      .createFareRule({
        routeId: this.routeId(),
        seatClass: value.seatClass,
        validFrom: new Date(value.validFrom).toISOString(),
        validTo: new Date(value.validTo).toISOString(),
        surchargeRate: value.surchargeRate,
      })
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rule) => {
          this.fareRules.update((current) => [...current, rule]);
          this.ruleForm.reset({ seatClass: 'economy', validFrom: '', validTo: '', surchargeRate: 0 });
          this.notifications.success(`Fare rule for ${rule.seatClass} added.`);
        },
      });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = ''; // allow re-selecting the same file after a fix
    if (!file) {
      return;
    }

    this.previewFileName.set(file.name);
    const reader = new FileReader();
    reader.onload = () => this.parsePreview(String(reader.result ?? ''));
    reader.readAsText(file);
  }

  clearPreview(): void {
    this.previewRows.set([]);
    this.previewFileName.set(null);
  }

  confirmUpload(): void {
    const rows = this.previewRows();
    if (rows.length === 0 || this.hasPreviewErrors() || this.uploading()) {
      return;
    }
    const requests: FareRuleRequest[] = rows.map((row) => ({
      routeId: this.routeId(),
      seatClass: row.seatClass,
      validFrom: new Date(row.validFrom).toISOString(),
      validTo: new Date(row.validTo).toISOString(),
      surchargeRate: Number(row.surchargeRate),
    }));

    this.uploading.set(true);
    this.fareRuleService
      .bulkCreateFareRules(requests)
      .pipe(finalize(() => this.uploading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (created) => {
          this.fareRules.update((current) => [...current, ...created]);
          this.notifications.success(`${created.length} fare rule(s) loaded.`);
          this.clearPreview();
        },
      });
  }

  private parsePreview(text: string): void {
    const lines = text
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line.length > 0);

    if (lines.length === 0) {
      this.notifications.error('That file is empty.');
      this.previewRows.set([]);
      return;
    }

    const [header, ...dataLines] = lines;
    const columns = header.split(',').map((cell) => cell.trim().toLowerCase());
    if (columns.length !== CSV_HEADER.length || columns.some((col, i) => col !== CSV_HEADER[i])) {
      this.notifications.error('CSV header must be exactly: seatClass,validFrom,validTo,surchargeRate');
      this.previewRows.set([]);
      return;
    }

    this.previewRows.set(
      dataLines.map((line, index) => {
        const cells = line.split(',').map((cell) => cell.trim());
        const [seatClass, validFrom, validTo, surchargeRate] = cells;
        return {
          line: index + 2,
          seatClass: seatClass ?? '',
          validFrom: validFrom ?? '',
          validTo: validTo ?? '',
          surchargeRate: surchargeRate ?? '',
          error: this.validateRow(cells),
        };
      }),
    );
  }

  private validateRow(cells: string[]): string | null {
    if (cells.length !== 4) {
      return `Expected 4 columns, found ${cells.length}`;
    }
    const [seatClass, validFrom, validTo, surchargeRate] = cells;
    if (!seatClass) {
      return 'seatClass is required';
    }
    const from = Date.parse(validFrom);
    const to = Date.parse(validTo);
    if (Number.isNaN(from)) {
      return 'validFrom is not a valid date';
    }
    if (Number.isNaN(to)) {
      return 'validTo is not a valid date';
    }
    if (from >= to) {
      return 'validFrom must be before validTo';
    }
    const rate = Number(surchargeRate);
    if (Number.isNaN(rate)) {
      return 'surchargeRate is not a number';
    }
    if (rate < -1 || rate > 5) {
      return 'surchargeRate must be between -1 and 5';
    }
    return null;
  }
}

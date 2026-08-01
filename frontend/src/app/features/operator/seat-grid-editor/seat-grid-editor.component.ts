import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, finalize, forkJoin, of } from 'rxjs';
import { SeatResponse, SeatStatus } from '../../../core/models/catalog.model';
import { InventoryManagementService } from '../../../core/services/inventory-management.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ScheduleService } from '../../../core/services/schedule.service';

/** Statuses an operator may set directly. HELD/BOOKED only ever come from the
 *  customer hold/checkout lifecycle and are read-only here. */
const OPERATOR_STATUSES: SeatStatus[] = ['AVAILABLE', 'BLOCKED', 'RESERVED_OPERATOR'];

/** Leading digits of a seat number are treated as its "row" for batch actions
 *  (e.g. "12A" -> row "12"); seat numbers with no leading digits are their
 *  own single-seat row. */
function rowOf(seatNumber: string): string {
  return seatNumber.match(/^\d+/)?.[0] ?? seatNumber;
}

@Component({
  selector: 'tw-seat-grid-editor',
  imports: [ReactiveFormsModule],
  templateUrl: './seat-grid-editor.component.html',
  styleUrl: './seat-grid-editor.component.scss',
})
export class SeatGridEditorComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly scheduleService = inject(ScheduleService);
  private readonly inventoryService = inject(InventoryManagementService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly scheduleId = input.required<number>();

  readonly seats = signal<SeatResponse[]>([]);
  readonly loading = signal(true);
  readonly selectedSeatId = signal<number | null>(null);
  readonly savingSeatId = signal<number | null>(null);
  readonly addingSeat = signal(false);
  readonly selectedRow = signal<string | null>(null);
  readonly batchWorking = signal(false);

  readonly operatorStatuses = OPERATOR_STATUSES;

  readonly rows = computed(() => {
    const unique = new Set(this.seats().map((seat) => rowOf(seat.seatNumber)));
    return Array.from(unique).sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
  });

  readonly selectedSeat = computed(() => this.seats().find((seat) => seat.id === this.selectedSeatId()) ?? null);

  readonly selectedSeatLocked = computed(() => {
    const seat = this.selectedSeat();
    if (!seat) {
      return null;
    }
    if (seat.status === 'BOOKED') {
      return 'This seat is booked and can’t be modified here.';
    }
    if (seat.status === 'HELD' && seat.heldUntil && Date.parse(seat.heldUntil) > Date.now()) {
      return 'This seat is currently held by a customer mid-checkout.';
    }
    return null;
  });

  readonly addSeatForm = this.fb.nonNullable.group({
    seatNumber: ['', Validators.required],
    seatClass: ['economy', Validators.required],
    priceModifier: [1, [Validators.required, Validators.min(0)]],
  });

  readonly editForm = this.fb.nonNullable.group({
    status: this.fb.nonNullable.control<SeatStatus>('AVAILABLE'),
    priceModifier: [1, [Validators.required, Validators.min(0)]],
  });

  ngOnInit(): void {
    this.loadSeats();
  }

  private loadSeats(): void {
    this.loading.set(true);
    this.scheduleService
      .getSeats(this.scheduleId())
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (seats) => this.seats.set(seats) });
  }

  refresh(): void {
    this.loadSeats();
  }

  selectSeat(seat: SeatResponse): void {
    if (seat.id === this.selectedSeatId()) {
      this.selectedSeatId.set(null);
      return;
    }
    this.selectedSeatId.set(seat.id);
    this.editForm.setValue({
      status: OPERATOR_STATUSES.includes(seat.status) ? seat.status : 'AVAILABLE',
      priceModifier: seat.priceModifier,
    });
  }

  addSeat(): void {
    if (this.addSeatForm.invalid || this.addingSeat()) {
      return;
    }
    const value = this.addSeatForm.getRawValue();

    this.addingSeat.set(true);
    this.inventoryService
      .addSeat({
        scheduleId: this.scheduleId(),
        seatNumber: value.seatNumber,
        seatClass: value.seatClass,
        priceModifier: value.priceModifier,
      })
      .pipe(finalize(() => this.addingSeat.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (seat) => {
          this.seats.update((current) => [...current, seat]);
          this.addSeatForm.reset({ seatNumber: '', seatClass: 'economy', priceModifier: 1 });
          this.notifications.success(`Seat ${seat.seatNumber} added.`);
        },
      });
  }

  saveSeatEdit(): void {
    const seat = this.selectedSeat();
    if (!seat || this.selectedSeatLocked() || this.editForm.invalid || this.savingSeatId()) {
      return;
    }
    const value = this.editForm.getRawValue();

    this.savingSeatId.set(seat.id);
    this.inventoryService
      .updateSeat(seat.id, { status: value.status, priceModifier: value.priceModifier })
      .pipe(finalize(() => this.savingSeatId.set(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.mergeSeats([updated]);
          this.notifications.success(`Seat ${updated.seatNumber} updated.`);
        },
      });
  }

  onRowSelected(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.selectedRow.set(value || null);
  }

  blockRow(): void {
    this.applyToRow('BLOCKED');
  }

  reserveRowForOperator(): void {
    this.applyToRow('RESERVED_OPERATOR');
  }

  unblockRow(): void {
    this.applyToRow('AVAILABLE');
  }

  private applyToRow(status: SeatStatus): void {
    const row = this.selectedRow();
    if (!row || this.batchWorking()) {
      return;
    }
    const targets = this.seats().filter((seat) => rowOf(seat.seatNumber) === row && seat.status !== 'BOOKED');
    if (targets.length === 0) {
      return;
    }

    this.batchWorking.set(true);
    forkJoin(
      targets.map((seat) =>
        this.inventoryService
          .updateSeat(seat.id, { status, priceModifier: seat.priceModifier })
          .pipe(catchError(() => of(null))),
      ),
    )
      .pipe(finalize(() => this.batchWorking.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (results) => {
          const succeeded = results.filter((seat): seat is SeatResponse => seat !== null);
          this.mergeSeats(succeeded);
          const skipped = targets.length - succeeded.length;
          this.notifications.success(
            skipped > 0
              ? `Updated ${succeeded.length} seat(s) in row ${row}; ${skipped} couldn't be changed (held mid-checkout).`
              : `Updated ${succeeded.length} seat(s) in row ${row}.`,
          );
        },
      });
  }

  private mergeSeats(updated: SeatResponse[]): void {
    const byId = new Map(updated.map((seat) => [seat.id, seat]));
    this.seats.update((current) => current.map((seat) => byId.get(seat.id) ?? seat));
  }
}

import { DatePipe, CurrencyPipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';
import { BookingDetailResponse, BookingSearchResult } from '../../core/models/booking.model';
import { RefundResponse } from '../../core/models/payment.model';
import { BookingService } from '../../core/services/booking.service';
import { NotificationService } from '../../core/services/notification.service';
import { RefundService } from '../../core/services/refund.service';

/**
 * Support workspace for US7: omni-search across PNR/customer email/passenger
 * name, a booking detail + refund history view, and a fee-override control
 * for settling a PENDING refund. Kept to what the backend actually supports
 * today (no seat-reassignment wizard, no tiered agent roles) — see the
 * accompanying audit for what's intentionally out of scope.
 */
@Component({
  selector: 'tw-support-panel',
  imports: [ReactiveFormsModule, DatePipe, CurrencyPipe],
  templateUrl: './support-panel.component.html',
  styleUrl: './support-panel.component.scss',
})
export class SupportPanelComponent {
  private readonly fb = inject(FormBuilder);
  private readonly bookingService = inject(BookingService);
  private readonly refundService = inject(RefundService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly searchForm = this.fb.nonNullable.group({
    query: ['', Validators.required],
  });

  readonly searching = signal(false);
  readonly searchError = signal(false);
  readonly searched = signal(false);
  readonly results = signal<BookingSearchResult[]>([]);

  readonly loadingDetail = signal(false);
  readonly detailError = signal(false);
  readonly detail = signal<BookingDetailResponse | null>(null);
  readonly refunds = signal<RefundResponse[]>([]);

  readonly pendingRefund = computed(() => this.refunds().find((r) => r.status === 'PENDING') ?? null);
  readonly farePaid = computed(() => this.detail()?.booking.totalAmount ?? 0);
  readonly currentFee = computed(() => {
    const refund = this.pendingRefund();
    return refund ? this.farePaid() - refund.amount : 0;
  });

  readonly overrideForm = this.fb.nonNullable.group({
    waiveFee: [false],
    approveAmount: [0, [Validators.required, Validators.min(0)]],
    reason: [''],
  });
  readonly processingRefund = signal(false);

  readonly waivedAmount = computed(() => {
    const refund = this.pendingRefund();
    if (!refund || !this.overrideForm.controls.waiveFee.value) {
      return 0;
    }
    return Math.max(0, (this.overrideForm.controls.approveAmount.value ?? 0) - refund.amount);
  });

  search(): void {
    const query = this.searchForm.getRawValue().query.trim();
    if (!query || this.searching()) {
      return;
    }

    this.searching.set(true);
    this.searchError.set(false);
    this.searched.set(true);
    this.results.set([]);
    this.closeDetail();
    this.bookingService
      .searchBookings(query)
      .pipe(finalize(() => this.searching.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (results) => this.results.set(results),
        error: () => this.searchError.set(true),
      });
  }

  selectBooking(bookingId: number): void {
    this.loadingDetail.set(true);
    this.detailError.set(false);
    this.detail.set(null);
    this.refunds.set([]);

    forkJoin({
      detail: this.bookingService.getBooking(bookingId),
      refunds: this.refundService.listRefundsForBooking(bookingId),
    })
      .pipe(finalize(() => this.loadingDetail.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ detail, refunds }) => {
          this.detail.set(detail);
          this.refunds.set(refunds);
          this.overrideForm.reset({ waiveFee: false, approveAmount: 0, reason: '' });
          const pending = refunds.find((r) => r.status === 'PENDING');
          if (pending) {
            this.overrideForm.controls.approveAmount.setValue(pending.amount);
          }
        },
        error: () => this.detailError.set(true),
      });
  }

  closeDetail(): void {
    this.detail.set(null);
    this.detailError.set(false);
    this.refunds.set([]);
  }

  toggleWaiveFee(): void {
    const refund = this.pendingRefund();
    const waiving = this.overrideForm.controls.waiveFee.value;
    if (waiving && refund) {
      this.overrideForm.controls.approveAmount.setValue(this.farePaid());
    } else if (refund) {
      this.overrideForm.controls.approveAmount.setValue(refund.amount);
    }
  }

  approveRefund(): void {
    this.submitDecision('APPROVE');
  }

  rejectRefund(): void {
    this.submitDecision('REJECT');
  }

  private submitDecision(decision: 'APPROVE' | 'REJECT'): void {
    const refund = this.pendingRefund();
    if (!refund || this.processingRefund()) {
      return;
    }

    const { waiveFee, approveAmount, reason } = this.overrideForm.getRawValue();
    const applyOverride = decision === 'APPROVE' && waiveFee;
    if (applyOverride && !reason.trim()) {
      this.notifications.error('A reason is required when waiving the cancellation fee.');
      return;
    }

    this.processingRefund.set(true);
    this.refundService
      .processRefund(refund.id, decision, applyOverride ? approveAmount : null, applyOverride ? reason.trim() : null)
      .pipe(finalize(() => this.processingRefund.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.refunds.update((current) => current.map((r) => (r.id === updated.id ? updated : r)));
          this.overrideForm.reset({ waiveFee: false, approveAmount: 0, reason: '' });
          this.notifications.success(`Refund #${updated.id} is now ${updated.status}.`);
        },
      });
  }
}

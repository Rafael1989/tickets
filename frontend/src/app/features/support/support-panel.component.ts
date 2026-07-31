import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { BookingDetailResponse } from '../../core/models/booking.model';
import { RefundDecision } from '../../core/models/payment.model';
import { BookingService } from '../../core/services/booking.service';
import { NotificationService } from '../../core/services/notification.service';
import { RefundService } from '../../core/services/refund.service';

@Component({
  selector: 'tw-support-panel',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './support-panel.component.html',
  styleUrl: './support-panel.component.scss',
})
export class SupportPanelComponent {
  private readonly fb = inject(FormBuilder);
  private readonly bookingService = inject(BookingService);
  private readonly refundService = inject(RefundService);
  private readonly notifications = inject(NotificationService);

  readonly searching = signal(false);
  readonly searchError = signal(false);
  readonly booking = signal<BookingDetailResponse | null>(null);
  readonly processingRefund = signal(false);

  readonly pnrForm = this.fb.nonNullable.group({
    pnr: ['', Validators.required],
  });

  readonly refundForm = this.fb.nonNullable.group({
    refundId: [null as number | null, Validators.required],
    decision: this.fb.nonNullable.control<RefundDecision>('APPROVE'),
  });

  lookupByPnr(): void {
    const pnr = this.pnrForm.getRawValue().pnr.trim();
    if (!pnr || this.searching()) {
      return;
    }

    this.searching.set(true);
    this.searchError.set(false);
    this.booking.set(null);
    this.bookingService
      .getBookingByPnr(pnr)
      .pipe(finalize(() => this.searching.set(false)))
      .subscribe({
        next: (booking) => this.booking.set(booking),
        error: () => this.searchError.set(true),
      });
  }

  processRefund(): void {
    const value = this.refundForm.getRawValue();
    if (this.refundForm.invalid || this.processingRefund() || !value.refundId) {
      return;
    }

    this.processingRefund.set(true);
    this.refundService
      .processRefund(value.refundId, value.decision)
      .pipe(finalize(() => this.processingRefund.set(false)))
      .subscribe({
        next: (refund) => this.notifications.success(`Refund #${refund.id} is now ${refund.status}.`),
      });
  }
}

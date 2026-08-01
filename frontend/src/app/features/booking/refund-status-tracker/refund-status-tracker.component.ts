import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { RefundResponse } from '../../../core/models/payment.model';

type StepState = 'done' | 'current' | 'upcoming' | 'rejected';

interface TimelineStep {
  label: string;
  state: StepState;
}

/**
 * Visual 3-step timeline over the refund's actual PENDING -> PROCESSED |
 * REJECTED lifecycle (see RefundStatus on the backend) — deliberately not a
 * gateway-webhook tracker, since settlement here is a manual support/admin
 * review, not an async payment-provider callback.
 */
@Component({
  selector: 'tw-refund-status-tracker',
  imports: [CurrencyPipe, DatePipe],
  templateUrl: './refund-status-tracker.component.html',
  styleUrl: './refund-status-tracker.component.scss',
})
export class RefundStatusTrackerComponent {
  readonly refund = input.required<RefundResponse>();

  readonly steps = computed<TimelineStep[]>(() => {
    const status = this.refund().status;
    return [
      { label: 'Request received', state: 'done' },
      { label: 'Under review', state: status === 'PENDING' ? 'current' : 'done' },
      {
        label: status === 'REJECTED' ? 'Rejected' : 'Refund issued',
        state: status === 'PENDING' ? 'upcoming' : status === 'REJECTED' ? 'rejected' : 'done',
      },
    ];
  });

  readonly statusMessage = computed(() => {
    switch (this.refund().status) {
      case 'PENDING':
        return 'Your request is waiting on support review. This usually takes 1-2 business days.';
      case 'PROCESSED':
        return 'Your refund was approved and issued to your original payment method.';
      case 'REJECTED':
        return 'Your refund request was reviewed and rejected. Contact support for details.';
    }
  });
}

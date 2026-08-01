import { CurrencyPipe } from '@angular/common';
import {
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { RefundQuoteResponse, RefundResponse } from '../../../core/models/payment.model';
import { RefundService } from '../../../core/services/refund.service';

type WizardStep = 'loading' | 'review' | 'ineligible' | 'load-error';

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Self-service cancellation flow for a CONFIRMED booking: fetches a
 * non-mutating refund-quote preview so the customer can see the policy
 * outcome (full/partial/ineligible, amounts) before confirming, then
 * submits the actual POST .../refunds on confirm. Owns its own dialog
 * semantics (focus trap, Escape-to-close) since nothing else in the app
 * currently needs a generic modal shell.
 */
@Component({
  selector: 'tw-cancellation-wizard',
  imports: [CurrencyPipe],
  templateUrl: './cancellation-wizard.component.html',
  styleUrl: './cancellation-wizard.component.scss',
})
export class CancellationWizardComponent implements OnInit {
  private readonly refundService = inject(RefundService);
  private readonly destroyRef = inject(DestroyRef);

  readonly bookingId = input.required<number>();
  readonly pnr = input.required<string>();
  readonly closed = output<void>();
  readonly cancelled = output<RefundResponse>();

  private readonly dialog = viewChild<ElementRef<HTMLElement>>('dialog');

  readonly step = signal<WizardStep>('loading');
  readonly quote = signal<RefundQuoteResponse | null>(null);
  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);

  readonly refundRatePercent = computed(() => {
    const rate = this.quote()?.refundRate;
    return rate === null || rate === undefined ? null : Math.round(rate * 100);
  });

  readonly payoutMessage = computed(() => {
    switch (this.quote()?.paymentMethod) {
      case 'pix':
        return 'Once approved, Pix refunds are typically credited back within minutes.';
      case 'card':
        return 'Once approved, card refunds are sent to your issuer and may take 1-2 billing cycles to appear on your statement.';
      default:
        return 'Once approved, your refund is issued to your original payment method.';
    }
  });

  constructor() {
    afterNextRender(() => this.dialog()?.nativeElement.focus());
  }

  ngOnInit(): void {
    this.loadQuote();
  }

  retryLoad(): void {
    this.loadQuote();
  }

  confirmCancellation(): void {
    if (this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.submitError.set(null);
    this.refundService
      .initiateRefund(this.bookingId())
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (refund) => this.cancelled.emit(refund),
        error: () => this.submitError.set('We could not process your cancellation. Please try again.'),
      });
  }

  close(): void {
    if (this.submitting()) {
      return;
    }
    this.closed.emit();
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      event.preventDefault();
      this.close();
      return;
    }
    if (event.key !== 'Tab') {
      return;
    }

    const root = this.dialog()?.nativeElement;
    if (!root) {
      return;
    }
    const focusable = Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
    if (focusable.length === 0) {
      event.preventDefault();
      return;
    }

    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    const active = root.ownerDocument.activeElement;

    if (event.shiftKey && active === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && active === last) {
      event.preventDefault();
      first.focus();
    }
  }

  private loadQuote(): void {
    this.step.set('loading');
    this.refundService
      .getRefundQuote(this.bookingId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (quote) => {
          this.quote.set(quote);
          this.step.set(quote.eligible ? 'review' : 'ineligible');
        },
        error: () => this.step.set('load-error'),
      });
  }
}

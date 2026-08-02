import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { RefundQuoteResponse, RefundResponse } from '../../../core/models/payment.model';
import { RefundService } from '../../../core/services/refund.service';
import { CancellationWizardComponent } from './cancellation-wizard.component';

describe('CancellationWizardComponent', () => {
  let fixture: ComponentFixture<CancellationWizardComponent>;
  let component: CancellationWizardComponent;
  let refundService: RefundService;

  const eligibleQuote: RefundQuoteResponse = {
    bookingId: 500,
    fareAmount: 100,
    policyCode: 'FULL_REFUND',
    refundRate: 1,
    refundAmount: 100,
    nonRefundableAmount: 0,
    paymentMethod: 'card',
    eligible: true,
  };

  const ineligibleQuote: RefundQuoteResponse = {
    bookingId: 500,
    fareAmount: 100,
    policyCode: null,
    refundRate: null,
    refundAmount: 0,
    nonRefundableAmount: 100,
    paymentMethod: 'card',
    eligible: false,
  };

  async function createComponent(quoteResult: RefundQuoteResponse | 'error' = eligibleQuote) {
    await TestBed.configureTestingModule({
      imports: [CancellationWizardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    refundService = TestBed.inject(RefundService);
    if (quoteResult === 'error') {
      vi.spyOn(refundService, 'getRefundQuote').mockReturnValue(throwError(() => new Error('boom')));
    } else {
      vi.spyOn(refundService, 'getRefundQuote').mockReturnValue(of(quoteResult));
    }

    fixture = TestBed.createComponent(CancellationWizardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('bookingId', 500);
    fixture.componentRef.setInput('pnr', 'ABC123');
    fixture.detectChanges();
  }

  it('loads the quote on init and shows the review step when eligible', async () => {
    await createComponent(eligibleQuote);

    expect(component.step()).toBe('review');
    expect(component.quote()).toEqual(eligibleQuote);
  });

  it('shows the ineligible step when the quote reports eligible: false', async () => {
    await createComponent(ineligibleQuote);

    expect(component.step()).toBe('ineligible');
    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('too close to departure');
  });

  it('shows a load-error step and allows retrying when the quote fetch fails', async () => {
    await createComponent('error');

    expect(component.step()).toBe('load-error');

    vi.spyOn(refundService, 'getRefundQuote').mockReturnValue(of(eligibleQuote));
    component.retryLoad();

    expect(component.step()).toBe('review');
  });

  it('confirmCancellation submits the refund and emits cancelled on success', async () => {
    await createComponent(eligibleQuote);
    const refund: RefundResponse = {
      id: 1,
      paymentId: 1,
      amount: 100,
      policyCode: 'FULL_REFUND',
      status: 'PENDING',
      processedByUserId: null,
      processedAt: null,
      overrideDelta: null,
      overrideReason: null,
    };
    vi.spyOn(refundService, 'initiateRefund').mockReturnValue(of(refund));
    const emitted = vi.fn();
    component.cancelled.subscribe(emitted);

    component.confirmCancellation();

    expect(emitted).toHaveBeenCalledWith(refund);
    expect(component.submitting()).toBe(false);
  });

  it('confirmCancellation surfaces an inline error and does not emit on failure', async () => {
    await createComponent(eligibleQuote);
    vi.spyOn(refundService, 'initiateRefund').mockReturnValue(throwError(() => new Error('boom')));
    const emitted = vi.fn();
    component.cancelled.subscribe(emitted);

    component.confirmCancellation();

    expect(emitted).not.toHaveBeenCalled();
    expect(component.submitError()).toContain('could not process');
  });

  it('close emits closed unless a submission is in flight', async () => {
    await createComponent(eligibleQuote);
    const emitted = vi.fn();
    component.closed.subscribe(emitted);

    component.submitting.set(true);
    component.close();
    expect(emitted).not.toHaveBeenCalled();

    component.submitting.set(false);
    component.close();
    expect(emitted).toHaveBeenCalled();
  });

  it('Escape key closes the dialog', async () => {
    await createComponent(eligibleQuote);
    const emitted = vi.fn();
    component.closed.subscribe(emitted);

    component.onKeydown(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(emitted).toHaveBeenCalled();
  });
});

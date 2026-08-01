import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RefundResponse } from '../../../core/models/payment.model';
import { RefundStatusTrackerComponent } from './refund-status-tracker.component';

describe('RefundStatusTrackerComponent', () => {
  let fixture: ComponentFixture<RefundStatusTrackerComponent>;
  let component: RefundStatusTrackerComponent;

  function create(refund: RefundResponse) {
    TestBed.configureTestingModule({ imports: [RefundStatusTrackerComponent] });
    fixture = TestBed.createComponent(RefundStatusTrackerComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('refund', refund);
    fixture.detectChanges();
  }

  const base: RefundResponse = {
    id: 1,
    paymentId: 1,
    amount: 50,
    policyCode: 'PARTIAL_REFUND',
    status: 'PENDING',
    processedByUserId: null,
    processedAt: null,
  };

  it('marks "Under review" as current while PENDING', () => {
    create({ ...base, status: 'PENDING' });

    expect(component.steps().map((s) => s.state)).toEqual(['done', 'current', 'upcoming']);
  });

  it('marks the final step done when PROCESSED', () => {
    create({ ...base, status: 'PROCESSED', processedAt: '2026-01-05T00:00:00Z' });

    expect(component.steps().map((s) => s.state)).toEqual(['done', 'done', 'done']);
    expect(component.steps()[2].label).toBe('Refund issued');
  });

  it('marks the final step rejected when REJECTED', () => {
    create({ ...base, status: 'REJECTED', processedAt: '2026-01-05T00:00:00Z' });

    expect(component.steps().map((s) => s.state)).toEqual(['done', 'done', 'rejected']);
    expect(component.steps()[2].label).toBe('Rejected');
  });
});

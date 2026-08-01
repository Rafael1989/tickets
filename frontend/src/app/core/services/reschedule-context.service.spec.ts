import { TestBed } from '@angular/core/testing';
import { RescheduleContextService } from './reschedule-context.service';

describe('RescheduleContextService', () => {
  let service: RescheduleContextService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(RescheduleContextService);
  });

  it('starts with no context', () => {
    expect(service.context()).toBeNull();
  });

  it('start() records the booking id, passenger ids, and fare-settlement flag', () => {
    service.start(42, [1, 2], true);

    expect(service.context()).toEqual({ bookingId: 42, passengerIds: [1, 2], requiresFareSettlement: true });
  });

  it('clear() resets the context to null', () => {
    service.start(42, [1, 2], false);
    service.clear();

    expect(service.context()).toBeNull();
  });
});

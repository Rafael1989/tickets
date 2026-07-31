import { TestBed } from '@angular/core/testing';
import { ScheduleSearchResult, SeatResponse } from '../models/catalog.model';
import { BookingDraftService } from './booking-draft.service';

describe('BookingDraftService', () => {
  let service: BookingDraftService;

  const schedule: ScheduleSearchResult = {
    scheduleId: 1,
    routeId: 1,
    type: 'BUS',
    origin: 'NYC',
    destination: 'Boston',
    venue: null,
    departureTime: '2026-08-10T00:00:00Z',
    arrivalTime: '2026-08-10T04:00:00Z',
    baseFare: 20,
    currency: 'USD',
    status: 'SCHEDULED',
    availableSeats: 3,
  };

  const seats: SeatResponse[] = [
    { id: 1, scheduleId: 1, seatNumber: '1A', seatClass: 'economy', status: 'AVAILABLE', priceModifier: 1 },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(BookingDraftService);
  });

  it('starts with no draft', () => {
    expect(service.draft()).toBeNull();
  });

  it('set stores the schedule and seats', () => {
    service.set({ schedule, seats });

    expect(service.draft()).toEqual({ schedule, seats });
  });

  it('clear resets the draft to null', () => {
    service.set({ schedule, seats });

    service.clear();

    expect(service.draft()).toBeNull();
  });
});

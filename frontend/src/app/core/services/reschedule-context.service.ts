import { Injectable, signal } from '@angular/core';

export interface RescheduleContext {
  bookingId: number;
  /** The original booking's passenger ids, reused positionally against
   *  whatever new seats get selected — rescheduling doesn't re-collect
   *  passenger info, it just moves the same travelers to a new schedule. */
  passengerIds: number[];
  /** True when the original booking is CONFIRMED (paid): seat-selection must
   *  preview the fare difference and may need to collect a top-up payment
   *  before confirming, instead of the free INITIATED-booking swap. */
  requiresFareSettlement: boolean;
}

/**
 * Set by the booking-details page before sending the user to search for a
 * new schedule, and read by seat-selection to switch its "continue" action
 * from creating a new booking to rescheduling this one in place.
 */
@Injectable({ providedIn: 'root' })
export class RescheduleContextService {
  private readonly contextSignal = signal<RescheduleContext | null>(null);
  readonly context = this.contextSignal.asReadonly();

  start(bookingId: number, passengerIds: number[], requiresFareSettlement: boolean): void {
    this.contextSignal.set({ bookingId, passengerIds, requiresFareSettlement });
  }

  clear(): void {
    this.contextSignal.set(null);
  }
}

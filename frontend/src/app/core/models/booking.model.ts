export type BookingStatus = 'INITIATED' | 'PAYMENT_PROCESSING' | 'CONFIRMED' | 'FAILED' | 'CANCELLED';

export interface SeatSelection {
  seatId: number;
  passengerId: number;
}

export interface CreateBookingRequest {
  scheduleId: number;
  seatSelections: SeatSelection[];
  promoCode?: string | null;
}

/**
 * For an INITIATED (unpaid) booking, the payment fields are ignored. For a
 * CONFIRMED (paid) booking, they're only required when the new seats' fare
 * is a net increase over the current total (see RescheduleQuoteResponse's
 * paymentRequired) — the backend collects that difference the same way
 * checkout collects an initial payment.
 */
export interface RescheduleRequest {
  scheduleId: number;
  seatSelections: SeatSelection[];
  paymentMethod?: string | null;
  paymentReference?: string | null;
  cardNumber?: string | null;
}

/** Non-mutating preview of GET /api/bookings/{id}/reschedule-quote. */
export interface RescheduleQuoteResponse {
  bookingId: number;
  currentTotal: number;
  newTotal: number;
  fareDifference: number;
  eligible: boolean;
  paymentRequired: boolean;
}

export interface BookingResponse {
  id: number;
  userId: number;
  scheduleId: number;
  pnr: string;
  bookingTime: string;
  status: BookingStatus;
  totalAmount: number;
  promoCode: string | null;
}

export interface BookingItemResponse {
  id: number;
  bookingId: number;
  seatId: number;
  passengerId: number;
  fare: number;
}

export interface BookingDetailResponse {
  booking: BookingResponse;
  items: BookingItemResponse[];
}

/** A row in the support omni-search results list — GET /api/bookings/search. */
export interface BookingSearchResult {
  bookingId: number;
  pnr: string;
  status: BookingStatus;
  totalAmount: number;
  bookingTime: string;
  customerUsername: string;
  customerEmail: string;
  origin: string | null;
  destination: string | null;
  departureTime: string;
}

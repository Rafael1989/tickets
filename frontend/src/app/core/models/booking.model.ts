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

/** Only accepted by the backend while the booking is still INITIATED (unpaid). */
export interface RescheduleRequest {
  scheduleId: number;
  seatSelections: SeatSelection[];
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

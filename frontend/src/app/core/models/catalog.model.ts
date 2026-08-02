export type RouteType = 'FLIGHT' | 'BUS' | 'TRAIN' | 'EVENT';

export type ScheduleStatus = 'SCHEDULED' | 'DELAYED' | 'CANCELLED' | 'COMPLETED';

export type SeatStatus = 'AVAILABLE' | 'HELD' | 'BOOKED' | 'BLOCKED' | 'RESERVED_OPERATOR';

export type ScheduleSortBy = 'DEPARTURE_TIME' | 'PRICE_ASC' | 'PRICE_DESC';

export interface ScheduleSearchCriteria {
  type?: RouteType | '';
  origin?: string;
  destination?: string;
  venue?: string;
  departureDate?: string;
  minPrice?: number | null;
  maxPrice?: number | null;
  seatClass?: string;
  sortBy?: ScheduleSortBy | '';
}

export interface ScheduleSearchResult {
  scheduleId: number;
  routeId: number;
  type: RouteType;
  origin: string | null;
  destination: string | null;
  venue: string | null;
  departureTime: string;
  arrivalTime: string;
  baseFare: number;
  currency: string;
  status: ScheduleStatus;
  availableSeats: number;
}

export interface SeatResponse {
  id: number;
  scheduleId: number;
  seatNumber: string;
  seatClass: string;
  status: SeatStatus;
  priceModifier: number;
  estimatedFare: number | null;
  heldUntil: string | null;
  heldByMe: boolean;
}

/** The plain CRUD shape returned by schedule creation — distinct from
 * ScheduleSearchResult, which denormalizes route + a live seat count. */
export interface ScheduleResponse {
  id: number;
  routeId: number;
  departureTime: string;
  arrivalTime: string;
  baseFare: number;
  currency: string;
  status: ScheduleStatus;
  vehicleId: number | null;
  driverId: number | null;
}

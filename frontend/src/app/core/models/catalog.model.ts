export type RouteType = 'FLIGHT' | 'BUS' | 'TRAIN' | 'EVENT';

export type ScheduleStatus = 'SCHEDULED' | 'DELAYED' | 'CANCELLED' | 'COMPLETED';

export type SeatStatus = 'AVAILABLE' | 'HELD' | 'BOOKED';

export interface ScheduleSearchCriteria {
  type?: RouteType | '';
  origin?: string;
  destination?: string;
  venue?: string;
  departureDate?: string;
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
}

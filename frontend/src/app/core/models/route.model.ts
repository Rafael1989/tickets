import { RouteType } from './catalog.model';

export interface RouteRequest {
  type: RouteType;
  origin?: string | null;
  destination?: string | null;
  venue?: string | null;
  durationMinutes: number;
}

export interface RouteResponse {
  id: number;
  operatorId: number;
  type: RouteType;
  origin: string | null;
  destination: string | null;
  venue: string | null;
  durationMinutes: number | null;
}

export interface ScheduleRequest {
  routeId: number;
  departureTime: string;
  arrivalTime: string;
  baseFare: number;
  currency: string;
  status?: string | null;
}

export interface SeatRequest {
  scheduleId: number;
  seatNumber: string;
  seatClass: string;
  status?: string | null;
  priceModifier: number;
}

export interface SeatUpdateRequest {
  status: string;
  priceModifier: number;
}

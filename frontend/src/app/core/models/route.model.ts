import { RouteType, SeatStatus } from './catalog.model';

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
  vehicleId?: number | null;
  driverId?: number | null;
}

export interface VehicleRequest {
  type: RouteType;
  identifier: string;
  capacity: number;
  model?: string | null;
}

export interface VehicleResponse {
  id: number;
  operatorId: number;
  type: RouteType;
  identifier: string;
  capacity: number;
  model: string | null;
}

export interface DriverRequest {
  fullName: string;
  licenseNumber: string;
}

export interface DriverResponse {
  id: number;
  operatorId: number;
  fullName: string;
  licenseNumber: string;
}

export interface SeatRequest {
  scheduleId: number;
  seatNumber: string;
  seatClass: string;
  status?: string | null;
  priceModifier: number;
}

export interface SeatUpdateRequest {
  status: SeatStatus;
  priceModifier: number;
}

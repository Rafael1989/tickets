import { RouteType } from './catalog.model';

export interface RouteReportItem {
  routeId: number;
  type: RouteType;
  origin: string | null;
  destination: string | null;
  venue: string | null;
  confirmedBookings: number;
  revenue: number;
  totalSeats: number;
  bookedSeats: number;
  /** 0-1 fraction (e.g. 0.75 = 75% occupied); 0 if the route has no seats yet. */
  occupancyRate: number;
}

export interface OperatorReportResponse {
  routes: RouteReportItem[];
  totalConfirmedBookings: number;
  totalRevenue: number;
}

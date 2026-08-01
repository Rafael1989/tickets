import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ScheduleResponse } from '../models/catalog.model';
import { RouteRequest, RouteResponse } from '../models/route.model';

@Injectable({ providedIn: 'root' })
export class RouteService {
  constructor(private readonly http: HttpClient) {}

  createRoute(request: RouteRequest): Observable<RouteResponse> {
    return this.http.post<RouteResponse>('/api/routes', request);
  }

  updateRoute(routeId: number, request: RouteRequest): Observable<RouteResponse> {
    return this.http.put<RouteResponse>(`/api/routes/${routeId}`, request);
  }

  listMyRoutes(): Observable<RouteResponse[]> {
    return this.http.get<RouteResponse[]>('/api/routes/mine');
  }

  listSchedulesForRoute(routeId: number): Observable<ScheduleResponse[]> {
    return this.http.get<ScheduleResponse[]>(`/api/routes/${routeId}/schedules`);
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ScheduleResponse, SeatResponse } from '../models/catalog.model';
import { ScheduleRequest, SeatRequest, SeatUpdateRequest } from '../models/route.model';

@Injectable({ providedIn: 'root' })
export class InventoryManagementService {
  constructor(private readonly http: HttpClient) {}

  createSchedule(request: ScheduleRequest): Observable<ScheduleResponse> {
    return this.http.post<ScheduleResponse>('/api/schedules', request);
  }

  addSeat(request: SeatRequest): Observable<SeatResponse> {
    return this.http.post<SeatResponse>('/api/seats', request);
  }

  updateSeat(seatId: number, request: SeatUpdateRequest): Observable<SeatResponse> {
    return this.http.put<SeatResponse>(`/api/seats/${seatId}`, request);
  }
}

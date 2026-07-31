import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ScheduleSearchResult, SeatResponse } from '../models/catalog.model';

@Injectable({ providedIn: 'root' })
export class ScheduleService {
  constructor(private readonly http: HttpClient) {}

  getSchedule(scheduleId: number): Observable<ScheduleSearchResult> {
    return this.http.get<ScheduleSearchResult>(`/api/schedules/${scheduleId}`);
  }

  getSeats(scheduleId: number): Observable<SeatResponse[]> {
    return this.http.get<SeatResponse[]>(`/api/schedules/${scheduleId}/seats`);
  }
}

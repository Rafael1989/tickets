import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { OperatorReportResponse } from '../models/report.model';

@Injectable({ providedIn: 'root' })
export class OperatorReportService {
  constructor(private readonly http: HttpClient) {}

  /** OPERATOR only — confirmed bookings, revenue, and seat occupancy per route the caller can manage. */
  getReport(): Observable<OperatorReportResponse> {
    return this.http.get<OperatorReportResponse>('/api/operator/reports');
  }
}

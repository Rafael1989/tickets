import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { DriverRequest, DriverResponse } from '../models/route.model';

@Injectable({ providedIn: 'root' })
export class DriverService {
  constructor(private readonly http: HttpClient) {}

  createDriver(request: DriverRequest): Observable<DriverResponse> {
    return this.http.post<DriverResponse>('/api/drivers', request);
  }

  listMyDrivers(): Observable<DriverResponse[]> {
    return this.http.get<DriverResponse[]>('/api/drivers/mine');
  }
}

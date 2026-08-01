import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { VehicleRequest, VehicleResponse } from '../models/route.model';

@Injectable({ providedIn: 'root' })
export class VehicleService {
  constructor(private readonly http: HttpClient) {}

  createVehicle(request: VehicleRequest): Observable<VehicleResponse> {
    return this.http.post<VehicleResponse>('/api/vehicles', request);
  }

  listMyVehicles(): Observable<VehicleResponse[]> {
    return this.http.get<VehicleResponse[]>('/api/vehicles/mine');
  }
}

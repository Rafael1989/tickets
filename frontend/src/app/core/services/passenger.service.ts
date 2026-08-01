import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PassengerRequest, PassengerResponse } from '../models/passenger.model';

@Injectable({ providedIn: 'root' })
export class PassengerService {
  constructor(private readonly http: HttpClient) {}

  createPassenger(request: PassengerRequest): Observable<PassengerResponse> {
    return this.http.post<PassengerResponse>('/api/passengers', request);
  }

  listMyPassengers(): Observable<PassengerResponse[]> {
    return this.http.get<PassengerResponse[]>('/api/passengers/me');
  }

  updatePassenger(id: number, request: PassengerRequest): Observable<PassengerResponse> {
    return this.http.put<PassengerResponse>(`/api/passengers/${id}`, request);
  }

  deletePassenger(id: number): Observable<void> {
    return this.http.delete<void>(`/api/passengers/${id}`);
  }
}

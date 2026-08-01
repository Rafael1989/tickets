import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PromoValidationRequest, PromoValidationResponse } from '../models/promo.model';

@Injectable({ providedIn: 'root' })
export class PromoService {
  constructor(private readonly http: HttpClient) {}

  validate(request: PromoValidationRequest): Observable<PromoValidationResponse> {
    return this.http.post<PromoValidationResponse>('/api/promos/validate', request);
  }
}

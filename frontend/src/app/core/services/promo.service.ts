import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  PromoCodeRequest,
  PromoCodeResponse,
  PromoValidationRequest,
  PromoValidationResponse,
} from '../models/promo.model';

@Injectable({ providedIn: 'root' })
export class PromoService {
  constructor(private readonly http: HttpClient) {}

  validate(request: PromoValidationRequest): Observable<PromoValidationResponse> {
    return this.http.post<PromoValidationResponse>('/api/promos/validate', request);
  }

  /** ADMIN only. */
  listPromoCodes(): Observable<PromoCodeResponse[]> {
    return this.http.get<PromoCodeResponse[]>('/api/promos');
  }

  /** ADMIN only. */
  createPromoCode(request: PromoCodeRequest): Observable<PromoCodeResponse> {
    return this.http.post<PromoCodeResponse>('/api/promos', request);
  }

  /** ADMIN only. */
  updateStatus(promoCodeId: number, active: boolean): Observable<PromoCodeResponse> {
    return this.http.put<PromoCodeResponse>(`/api/promos/${promoCodeId}/status`, { active });
  }
}

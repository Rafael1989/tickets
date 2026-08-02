import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import {
  PartnerCredentialIssuedResponse,
  PartnerCredentialResponse,
  PartnerRequest,
  PartnerResponse,
  PartnerStatus,
  PartnerWebhookIssuedResponse,
  PartnerWebhookRequest,
  PartnerWebhookResponse,
  WebhookStatus,
} from '../models/partner.model';

@Injectable({ providedIn: 'root' })
export class PartnerService {
  constructor(private readonly http: HttpClient) {}

  listPartners(): Observable<PartnerResponse[]> {
    return this.http.get<PartnerResponse[]>('/api/partners');
  }

  createPartner(request: PartnerRequest): Observable<PartnerResponse> {
    return this.http.post<PartnerResponse>('/api/partners', request);
  }

  updateStatus(partnerId: number, status: PartnerStatus): Observable<PartnerResponse> {
    return this.http.put<PartnerResponse>(`/api/partners/${partnerId}/status`, { status });
  }

  listCredentials(partnerId: number): Observable<PartnerCredentialResponse[]> {
    return this.http.get<PartnerCredentialResponse[]>(`/api/partners/${partnerId}/credentials`);
  }

  issueCredential(partnerId: number): Observable<PartnerCredentialIssuedResponse> {
    return this.http.post<PartnerCredentialIssuedResponse>(`/api/partners/${partnerId}/credentials`, {});
  }

  revokeCredential(credentialId: number): Observable<void> {
    return this.http.put<void>(`/api/partners/credentials/${credentialId}/revoke`, {});
  }

  listWebhooks(partnerId: number): Observable<PartnerWebhookResponse[]> {
    return this.http.get<PartnerWebhookResponse[]>(`/api/partners/${partnerId}/webhooks`);
  }

  registerWebhook(partnerId: number, request: PartnerWebhookRequest): Observable<PartnerWebhookIssuedResponse> {
    return this.http.post<PartnerWebhookIssuedResponse>(`/api/partners/${partnerId}/webhooks`, request);
  }

  updateWebhookStatus(webhookId: number, status: WebhookStatus): Observable<PartnerWebhookResponse> {
    return this.http.put<PartnerWebhookResponse>(`/api/partners/webhooks/${webhookId}/status`, { status });
  }
}

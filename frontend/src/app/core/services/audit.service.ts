import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuditLogResponse, AuditLogSearchParams } from '../models/audit.model';

@Injectable({ providedIn: 'root' })
export class AuditService {
  constructor(private readonly http: HttpClient) {}

  listAudit(params: AuditLogSearchParams = {}): Observable<AuditLogResponse[]> {
    let httpParams = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null && value !== '') {
        httpParams = httpParams.set(key, String(value));
      }
    }
    return this.http.get<AuditLogResponse[]>('/api/audit', { params: httpParams });
  }
}

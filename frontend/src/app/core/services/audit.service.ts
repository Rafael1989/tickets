import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuditLogResponse } from '../models/audit.model';

@Injectable({ providedIn: 'root' })
export class AuditService {
  constructor(private readonly http: HttpClient) {}

  listAudit(): Observable<AuditLogResponse[]> {
    return this.http.get<AuditLogResponse[]>('/api/audit');
  }
}

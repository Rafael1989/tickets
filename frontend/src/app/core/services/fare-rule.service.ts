import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { FareRuleRequest, FareRuleResponse } from '../models/fare-rule.model';

@Injectable({ providedIn: 'root' })
export class FareRuleService {
  constructor(private readonly http: HttpClient) {}

  createFareRule(request: FareRuleRequest): Observable<FareRuleResponse> {
    return this.http.post<FareRuleResponse>('/api/fare-rules', request);
  }

  bulkCreateFareRules(requests: FareRuleRequest[]): Observable<FareRuleResponse[]> {
    return this.http.post<FareRuleResponse[]>('/api/fare-rules/bulk', requests);
  }

  listFareRulesForRoute(routeId: number): Observable<FareRuleResponse[]> {
    return this.http.get<FareRuleResponse[]>(`/api/routes/${routeId}/fare-rules`);
  }
}

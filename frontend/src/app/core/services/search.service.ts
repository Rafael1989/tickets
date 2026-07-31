import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ScheduleSearchCriteria, ScheduleSearchResult } from '../models/catalog.model';

@Injectable({ providedIn: 'root' })
export class SearchService {
  constructor(private readonly http: HttpClient) {}

  search(criteria: ScheduleSearchCriteria): Observable<ScheduleSearchResult[]> {
    let params = new HttpParams();
    if (criteria.type) {
      params = params.set('type', criteria.type);
    }
    if (criteria.origin) {
      params = params.set('origin', criteria.origin);
    }
    if (criteria.destination) {
      params = params.set('destination', criteria.destination);
    }
    if (criteria.venue) {
      params = params.set('venue', criteria.venue);
    }
    if (criteria.departureDate) {
      params = params.set('departureDate', criteria.departureDate);
    }

    return this.http.get<ScheduleSearchResult[]>('/api/search', { params });
  }
}

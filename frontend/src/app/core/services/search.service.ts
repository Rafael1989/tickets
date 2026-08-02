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
    if (criteria.minPrice != null) {
      params = params.set('minPrice', criteria.minPrice);
    }
    if (criteria.maxPrice != null) {
      params = params.set('maxPrice', criteria.maxPrice);
    }
    if (criteria.seatClass) {
      params = params.set('seatClass', criteria.seatClass);
    }
    if (criteria.sortBy) {
      params = params.set('sortBy', criteria.sortBy);
    }

    return this.http.get<ScheduleSearchResult[]>('/api/search', { params });
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { UserPreferencesRequest, UserPreferencesResponse } from '../models/preferences.model';

@Injectable({ providedIn: 'root' })
export class PreferencesService {
  constructor(private readonly http: HttpClient) {}

  getPreferences(): Observable<UserPreferencesResponse> {
    return this.http.get<UserPreferencesResponse>('/api/users/me/preferences');
  }

  updatePreferences(request: UserPreferencesRequest): Observable<UserPreferencesResponse> {
    return this.http.put<UserPreferencesResponse>('/api/users/me/preferences', request);
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ChangePasswordRequest, UpdateEmailRequest, UserResponse } from '../models/admin-user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private readonly http: HttpClient) {}

  getCurrentUser(): Observable<UserResponse> {
    return this.http.get<UserResponse>('/api/users/me');
  }

  updateEmail(request: UpdateEmailRequest): Observable<UserResponse> {
    return this.http.put<UserResponse>('/api/users/me/email', request);
  }

  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.put<void>('/api/users/me/password', request);
  }
}

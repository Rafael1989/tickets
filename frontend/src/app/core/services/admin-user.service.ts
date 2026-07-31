import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RoleUpdateRequest, UserRequest, UserResponse } from '../models/admin-user.model';

@Injectable({ providedIn: 'root' })
export class AdminUserService {
  constructor(private readonly http: HttpClient) {}

  listUsers(): Observable<UserResponse[]> {
    return this.http.get<UserResponse[]>('/api/users');
  }

  getUser(userId: number): Observable<UserResponse> {
    return this.http.get<UserResponse>(`/api/users/${userId}`);
  }

  createUser(request: UserRequest): Observable<UserResponse> {
    return this.http.post<UserResponse>('/api/users', request);
  }

  updateRole(userId: number, request: RoleUpdateRequest): Observable<UserResponse> {
    return this.http.put<UserResponse>(`/api/users/${userId}/role`, request);
  }
}

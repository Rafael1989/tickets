import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { JwtPayload, LoginRequest, LoginResponse, RegisterRequest } from '../models/auth.model';

const TOKEN_STORAGE_KEY = 'tw.accessToken';

function decodeJwtPayload(token: string): JwtPayload | null {
  const segments = token.split('.');
  if (segments.length !== 3) {
    return null;
  }
  try {
    const normalized = segments[1].replace(/-/g, '+').replace(/_/g, '/');
    const json = decodeURIComponent(
      atob(normalized)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    return JSON.parse(json) as JwtPayload;
  } catch {
    return null;
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenSignal = signal<string | null>(localStorage.getItem(TOKEN_STORAGE_KEY));

  readonly payload = computed<JwtPayload | null>(() => {
    const token = this.tokenSignal();
    return token ? decodeJwtPayload(token) : null;
  });

  readonly isAuthenticated = computed(() => this.payload() !== null);
  readonly username = computed(() => this.payload()?.sub ?? null);
  readonly roles = computed(() => this.payload()?.roles ?? []);

  constructor(private readonly http: HttpClient) {}

  get token(): string | null {
    return this.tokenSignal();
  }

  hasRole(role: string): boolean {
    return this.roles().includes(role);
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/login', request).pipe(
      tap((response) => {
        localStorage.setItem(TOKEN_STORAGE_KEY, response.accessToken);
        this.tokenSignal.set(response.accessToken);
      }),
    );
  }

  register(request: RegisterRequest): Observable<unknown> {
    return this.http.post('/api/register', request);
  }

  logout(): void {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    this.tokenSignal.set(null);
  }
}

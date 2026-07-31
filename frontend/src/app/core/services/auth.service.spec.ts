import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { JwtPayload } from '../models/auth.model';
import { AuthService } from './auth.service';

function fakeJwt(payload: Partial<JwtPayload>): string {
  const base64url = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

  const header = base64url({ alg: 'HS512' });
  const body = base64url({ sub: 'alice', roles: ['CUSTOMER'], iat: 1, exp: 9999999999, ...payload });
  return `${header}.${body}.fake-signature`;
}

describe('AuthService', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
    httpMock?.verify();
  });

  function createService(): AuthService {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    return TestBed.inject(AuthService);
  }

  it('starts unauthenticated when localStorage has no token', () => {
    const service = createService();

    expect(service.isAuthenticated()).toBe(false);
    expect(service.username()).toBeNull();
    expect(service.roles()).toEqual([]);
    expect(service.token).toBeNull();
  });

  it('restores an authenticated session from a token already in localStorage', () => {
    localStorage.setItem('tw.accessToken', fakeJwt({ sub: 'bob', roles: ['SUPPORT'] }));

    const service = createService();

    expect(service.isAuthenticated()).toBe(true);
    expect(service.username()).toBe('bob');
    expect(service.roles()).toEqual(['SUPPORT']);
  });

  it('login stores the returned token and updates signals', () => {
    const service = createService();
    const token = fakeJwt({ sub: 'alice', roles: ['CUSTOMER'] });

    service.login({ username: 'alice', password: 'secret' }).subscribe();

    const req = httpMock.expectOne('/api/login');
    expect(req.request.method).toBe('POST');
    req.flush({ accessToken: token, tokenType: 'Bearer', expiresInSeconds: 900 });

    expect(service.token).toBe(token);
    expect(service.isAuthenticated()).toBe(true);
    expect(service.username()).toBe('alice');
    expect(localStorage.getItem('tw.accessToken')).toBe(token);
  });

  it('register posts to /api/register', () => {
    const service = createService();

    service.register({ username: 'alice', password: 'secret', email: 'alice@example.com' }).subscribe();

    const req = httpMock.expectOne('/api/register');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('logout clears the token and signals', () => {
    localStorage.setItem('tw.accessToken', fakeJwt({}));
    const service = createService();
    expect(service.isAuthenticated()).toBe(true);

    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(service.token).toBeNull();
    expect(localStorage.getItem('tw.accessToken')).toBeNull();
  });

  it('hasRole reflects the roles claim from the token', () => {
    localStorage.setItem('tw.accessToken', fakeJwt({ roles: ['SUPPORT', 'ADMIN'] }));
    const service = createService();

    expect(service.hasRole('SUPPORT')).toBe(true);
    expect(service.hasRole('CUSTOMER')).toBe(false);
  });

  it('treats a malformed token as unauthenticated', () => {
    localStorage.setItem('tw.accessToken', 'not-a-valid-jwt');
    const service = createService();

    expect(service.isAuthenticated()).toBe(false);
    expect(service.username()).toBeNull();
  });

  it('treats an undecodable token payload as unauthenticated', () => {
    localStorage.setItem('tw.accessToken', 'header.not-base64!!.signature');
    const service = createService();

    expect(service.isAuthenticated()).toBe(false);
  });
});

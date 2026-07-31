import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    localStorage.clear();
    httpMock.verify();
  });

  it('adds an Authorization header when a token is present', () => {
    localStorage.setItem('tw.accessToken', 'header.eyJzdWIiOiJhbGljZSJ9.sig');

    http.get('/api/bookings/1').subscribe();

    const req = httpMock.expectOne('/api/bookings/1');
    expect(req.request.headers.get('Authorization')).toBe('Bearer header.eyJzdWIiOiJhbGljZSJ9.sig');
    req.flush({});
  });

  it('leaves the request untouched when there is no token', () => {
    http.get('/api/search').subscribe();

    const req = httpMock.expectOne('/api/search');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });
});

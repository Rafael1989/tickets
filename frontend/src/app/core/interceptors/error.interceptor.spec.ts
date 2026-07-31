import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let auth: AuthService;
  let router: Router;
  let notifications: NotificationService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    notifications = TestBed.inject(NotificationService);
  });

  afterEach(() => {
    localStorage.clear();
    httpMock.verify();
  });

  it('passes a successful response through untouched', () => {
    let result: unknown;
    http.get('/api/search').subscribe((r) => (result = r));

    httpMock.expectOne('/api/search').flush([{ scheduleId: 1 }]);

    expect(result).toEqual([{ scheduleId: 1 }]);
  });

  it('on 401: logs out, redirects to login, and notifies', () => {
    const logoutSpy = vi.spyOn(auth, 'logout');
    const navigateSpy = vi.spyOn(router, 'navigate');
    const errorSpy = vi.spyOn(notifications, 'error');

    http.get('/api/bookings/1').subscribe({ error: () => {} });

    httpMock.expectOne('/api/bookings/1').flush(
      { message: 'unauthorized' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(logoutSpy).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
    expect(errorSpy).toHaveBeenCalled();
  });

  it('on 4xx/5xx: notifies with the server-provided message', () => {
    const errorSpy = vi.spyOn(notifications, 'error');

    http.post('/api/bookings', {}).subscribe({ error: () => {} });

    httpMock
      .expectOne('/api/bookings')
      .flush({ message: 'Seat 5 is unavailable' }, { status: 409, statusText: 'Conflict' });

    expect(errorSpy).toHaveBeenCalledWith('Seat 5 is unavailable');
  });

  it('falls back to a generic message when the error body has none', () => {
    const errorSpy = vi.spyOn(notifications, 'error');

    http.post('/api/bookings', {}).subscribe({ error: () => {} });

    httpMock.expectOne('/api/bookings').flush(null, { status: 500, statusText: 'Server Error' });

    expect(errorSpy).toHaveBeenCalledWith('Something went wrong. Please try again.');
  });

  it('does not notify on a network-level error below 400', () => {
    const errorSpy = vi.spyOn(notifications, 'error');
    const logoutSpy = vi.spyOn(auth, 'logout');

    http.get('/api/search').subscribe({ error: () => {} });

    httpMock.expectOne('/api/search').error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown' });

    expect(errorSpy).not.toHaveBeenCalled();
    expect(logoutSpy).not.toHaveBeenCalled();
  });

  it('re-throws the error to the caller', () => {
    let caught: unknown;
    http.get('/api/bookings/1').subscribe({ error: (err) => (caught = err) });

    httpMock.expectOne('/api/bookings/1').flush({}, { status: 404, statusText: 'Not Found' });

    expect(caught).toBeTruthy();
  });
});

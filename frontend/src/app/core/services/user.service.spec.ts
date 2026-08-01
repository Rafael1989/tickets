import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UserService } from './user.service';

describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getCurrentUser requests /api/users/me', () => {
    service.getCurrentUser().subscribe();

    const req = httpMock.expectOne('/api/users/me');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 1, username: 'alice', email: 'alice@example.com', role: 'CUSTOMER', createdAt: '2026-01-01T00:00:00Z' });
  });

  it('updateEmail puts to /api/users/me/email', () => {
    service.updateEmail({ email: 'new@example.com' }).subscribe();

    const req = httpMock.expectOne('/api/users/me/email');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ email: 'new@example.com' });
    req.flush({ id: 1, username: 'alice', email: 'new@example.com', role: 'CUSTOMER', createdAt: '2026-01-01T00:00:00Z' });
  });

  it('changePassword puts to /api/users/me/password', () => {
    const request = { currentPassword: 'old', newPassword: 'newpassword123' };
    service.changePassword(request).subscribe();

    const req = httpMock.expectOne('/api/users/me/password');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush(null);
  });
});

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RoleUpdateRequest, UserRequest } from '../models/admin-user.model';
import { AdminUserService } from './admin-user.service';

describe('AdminUserService', () => {
  let service: AdminUserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminUserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listUsers requests /api/users', () => {
    service.listUsers().subscribe();

    const req = httpMock.expectOne('/api/users');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('getUser requests /api/users/{id}', () => {
    service.getUser(3).subscribe();

    const req = httpMock.expectOne('/api/users/3');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('createUser posts the request to /api/users', () => {
    const request: UserRequest = {
      username: 'newop',
      password: 'secret123',
      email: 'newop@example.com',
      role: 'OPERATOR',
    };

    service.createUser(request).subscribe();

    const req = httpMock.expectOne('/api/users');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('updateRole puts the request to /api/users/{id}/role', () => {
    const request: RoleUpdateRequest = { role: 'SUPPORT' };

    service.updateRole(5, request).subscribe();

    const req = httpMock.expectOne('/api/users/5/role');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });
});

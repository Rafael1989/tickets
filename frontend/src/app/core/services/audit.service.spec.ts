import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuditService } from './audit.service';

describe('AuditService', () => {
  let service: AuditService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuditService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listAudit requests /api/audit with no params by default', () => {
    service.listAudit().subscribe();

    const req = httpMock.expectOne('/api/audit');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('listAudit includes only the given filters as query params', () => {
    service.listAudit({ actor: 'alice', action: 'USER_ROLE_CHANGED' }).subscribe();

    const req = httpMock.expectOne('/api/audit?actor=alice&action=USER_ROLE_CHANGED');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });
});

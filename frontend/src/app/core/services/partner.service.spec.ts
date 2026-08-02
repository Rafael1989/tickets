import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PartnerService } from './partner.service';

describe('PartnerService', () => {
  let service: PartnerService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PartnerService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listPartners gets /api/partners', () => {
    service.listPartners().subscribe();

    const req = httpMock.expectOne('/api/partners');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('createPartner posts the request to /api/partners', () => {
    const request = { name: 'Acme Transit', contactEmail: 'ops@acme.example', commissionRate: 0.1 };
    service.createPartner(request).subscribe();

    const req = httpMock.expectOne('/api/partners');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('updateStatus puts the status to /api/partners/{id}/status', () => {
    service.updateStatus(9, 'ACTIVE').subscribe();

    const req = httpMock.expectOne('/api/partners/9/status');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ status: 'ACTIVE' });
    req.flush({});
  });

  it('listCredentials gets /api/partners/{id}/credentials', () => {
    service.listCredentials(9).subscribe();

    const req = httpMock.expectOne('/api/partners/9/credentials');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('issueCredential posts to /api/partners/{id}/credentials', () => {
    service.issueCredential(9).subscribe();

    const req = httpMock.expectOne('/api/partners/9/credentials');
    expect(req.request.method).toBe('POST');
    req.flush({});
  });

  it('revokeCredential puts to /api/partners/credentials/{id}/revoke', () => {
    service.revokeCredential(1).subscribe();

    const req = httpMock.expectOne('/api/partners/credentials/1/revoke');
    expect(req.request.method).toBe('PUT');
    req.flush(null);
  });

  it('listWebhooks gets /api/partners/{id}/webhooks', () => {
    service.listWebhooks(9).subscribe();

    const req = httpMock.expectOne('/api/partners/9/webhooks');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('registerWebhook posts the request to /api/partners/{id}/webhooks', () => {
    const request = { url: 'https://partner.example/hook', eventType: 'BOOKING_CANCELLED' };
    service.registerWebhook(9, request).subscribe();

    const req = httpMock.expectOne('/api/partners/9/webhooks');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('updateWebhookStatus puts the status to /api/partners/webhooks/{id}/status', () => {
    service.updateWebhookStatus(1, 'DISABLED').subscribe();

    const req = httpMock.expectOne('/api/partners/webhooks/1/status');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ status: 'DISABLED' });
    req.flush({});
  });
});

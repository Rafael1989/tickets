import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { UserPreferencesRequest } from '../models/preferences.model';
import { PreferencesService } from './preferences.service';

describe('PreferencesService', () => {
  let service: PreferencesService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PreferencesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getPreferences requests /api/users/me/preferences', () => {
    service.getPreferences().subscribe();

    const req = httpMock.expectOne('/api/users/me/preferences');
    expect(req.request.method).toBe('GET');
    req.flush({ userId: 1, preferredCurrency: 'USD', seatPreference: null, notificationsEnabled: true, updatedAt: '2026-01-01T00:00:00Z' });
  });

  it('updatePreferences puts to /api/users/me/preferences', () => {
    const request: UserPreferencesRequest = {
      preferredCurrency: 'EUR',
      seatPreference: 'AISLE',
      notificationsEnabled: false,
    };

    service.updatePreferences(request).subscribe();

    const req = httpMock.expectOne('/api/users/me/preferences');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({ userId: 1, ...request, updatedAt: '2026-01-01T00:00:00Z' });
  });
});

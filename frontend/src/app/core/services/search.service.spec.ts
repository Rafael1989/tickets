import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ScheduleSearchResult } from '../models/catalog.model';
import { SearchService } from './search.service';

describe('SearchService', () => {
  let service: SearchService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SearchService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('sends only the criteria fields that are present', () => {
    service
      .search({
        type: 'BUS',
        origin: 'NYC',
        destination: 'Boston',
        venue: 'Arena',
        departureDate: '2026-08-10',
      })
      .subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === '/api/search' && r.params.get('type') === 'BUS' && r.params.get('origin') === 'NYC',
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('destination')).toBe('Boston');
    expect(req.request.params.get('venue')).toBe('Arena');
    expect(req.request.params.get('departureDate')).toBe('2026-08-10');
    req.flush([]);
  });

  it('omits every criteria field when the search is empty', () => {
    service.search({}).subscribe();

    const req = httpMock.expectOne('/api/search');
    expect(req.request.params.keys().length).toBe(0);
    req.flush([]);
  });

  it('returns the schedules from the response body', () => {
    const results: ScheduleSearchResult[] = [
      {
        scheduleId: 1,
        routeId: 1,
        type: 'BUS',
        origin: 'NYC',
        destination: 'Boston',
        venue: null,
        departureTime: '2026-08-10T00:00:00Z',
        arrivalTime: '2026-08-10T04:00:00Z',
        baseFare: 20,
        currency: 'USD',
        status: 'SCHEDULED',
        availableSeats: 3,
      },
    ];

    let actual: ScheduleSearchResult[] | undefined;
    service.search({}).subscribe((r) => (actual = r));

    httpMock.expectOne('/api/search').flush(results);

    expect(actual).toEqual(results);
  });
});

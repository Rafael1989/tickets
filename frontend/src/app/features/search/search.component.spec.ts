import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { ScheduleSearchResult } from '../../core/models/catalog.model';
import { SearchService } from '../../core/services/search.service';
import { SearchComponent } from './search.component';

describe('SearchComponent', () => {
  let fixture: ComponentFixture<SearchComponent>;
  let component: SearchComponent;
  let searchService: SearchService;

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

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SearchComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(SearchComponent);
    component = fixture.componentInstance;
    searchService = TestBed.inject(SearchService);
  });

  it('creates with no results and hasSearched false', () => {
    fixture.detectChanges();

    expect(component.hasSearched()).toBe(false);
    expect(component.results()).toEqual([]);
  });

  it('search populates results and flips hasSearched on success', () => {
    vi.spyOn(searchService, 'search').mockReturnValue(of(results));
    fixture.detectChanges();

    component.search();

    expect(component.loading()).toBe(false);
    expect(component.hasSearched()).toBe(true);
    expect(component.results()).toEqual(results);

    fixture.detectChanges();
    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('Boston');
  });

  it('renders the empty-results message when the search returns nothing', () => {
    vi.spyOn(searchService, 'search').mockReturnValue(of([]));
    fixture.detectChanges();

    component.search();
    fixture.detectChanges();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('No schedules match your search');
  });

  it('passes the current form value to the search service', () => {
    const searchSpy = vi.spyOn(searchService, 'search').mockReturnValue(of([]));
    fixture.detectChanges();

    component.form.patchValue({ origin: 'NYC', type: 'BUS' });
    component.search();

    expect(searchSpy).toHaveBeenCalledWith(expect.objectContaining({ origin: 'NYC', type: 'BUS' }));
  });

  it('renders an event result that has a venue but no destination', () => {
    const eventResult: ScheduleSearchResult = {
      scheduleId: 2,
      routeId: 2,
      type: 'EVENT',
      origin: null,
      destination: null,
      venue: 'Madison Square Garden',
      departureTime: '2026-09-01T00:00:00Z',
      arrivalTime: '2026-09-01T02:00:00Z',
      baseFare: 50,
      currency: 'USD',
      status: 'SCHEDULED',
      availableSeats: 10,
    };
    vi.spyOn(searchService, 'search').mockReturnValue(of([eventResult]));
    fixture.detectChanges();

    component.search();
    fixture.detectChanges();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('Madison Square Garden');
    expect(html).not.toContain('&rarr;');
  });

  it('shows a searching indicator while the request is in flight', () => {
    const subject = new Subject<ScheduleSearchResult[]>();
    vi.spyOn(searchService, 'search').mockReturnValue(subject);
    fixture.detectChanges();

    component.search();
    fixture.detectChanges();

    expect(component.loading()).toBe(true);
    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('Searching…');

    subject.next([]);
    subject.complete();
    fixture.detectChanges();

    expect(component.loading()).toBe(false);
  });
});

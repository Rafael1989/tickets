import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { OperatorReportResponse, RouteReportItem } from '../../../core/models/report.model';
import { OperatorReportService } from '../../../core/services/operator-report.service';
import { AnalyticsDashboardComponent } from './analytics-dashboard.component';

describe('AnalyticsDashboardComponent', () => {
  let fixture: ComponentFixture<AnalyticsDashboardComponent>;
  let component: AnalyticsDashboardComponent;
  let reportService: OperatorReportService;

  const routeItem: RouteReportItem = {
    routeId: 1,
    type: 'BUS',
    origin: 'NYC',
    destination: 'Boston',
    venue: null,
    confirmedBookings: 5,
    revenue: 100,
    totalSeats: 20,
    bookedSeats: 15,
    occupancyRate: 0.75,
  };

  const eventItem: RouteReportItem = {
    routeId: 2,
    type: 'EVENT',
    origin: null,
    destination: null,
    venue: 'Arena',
    confirmedBookings: 2,
    revenue: 50,
    totalSeats: 10,
    bookedSeats: 0,
    occupancyRate: 0,
  };

  const report: OperatorReportResponse = {
    routes: [routeItem, eventItem],
    totalConfirmedBookings: 7,
    totalRevenue: 150,
  };

  async function createComponent(response: OperatorReportResponse | 'error' = report): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AnalyticsDashboardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    reportService = TestBed.inject(OperatorReportService);
    if (response === 'error') {
      vi.spyOn(reportService, 'getReport').mockReturnValue(throwError(() => new Error('boom')));
    } else {
      vi.spyOn(reportService, 'getReport').mockReturnValue(of(response));
    }

    fixture = TestBed.createComponent(AnalyticsDashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('loads the report on init', async () => {
    await createComponent();

    expect(component.loading()).toBe(false);
    expect(component.report()).toEqual(report);
  });

  it('sets loadError when the report cannot be fetched', async () => {
    await createComponent('error');

    expect(component.loading()).toBe(false);
    expect(component.loadError()).toBe(true);
  });

  it('routeLabel prefers the venue for an event route', () => {
    expect(component.routeLabel(eventItem)).toBe('Arena');
  });

  it('routeLabel shows origin -> destination for a point-to-point route', () => {
    expect(component.routeLabel(routeItem)).toBe('NYC → Boston');
  });

  it('occupancyPercent rounds the 0-1 rate to a whole percentage', () => {
    expect(component.occupancyPercent(0.754)).toBe(75);
    expect(component.occupancyPercent(0)).toBe(0);
    expect(component.occupancyPercent(1)).toBe(100);
  });

  it('renders a table row per route with revenue and occupancy', async () => {
    await createComponent();
    fixture.detectChanges();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('NYC → Boston');
    expect(html).toContain('Arena');
    expect(html).toContain('75% (15/20)');
  });

  it('shows an empty-state message when the operator has no routes yet', async () => {
    await createComponent({ routes: [], totalConfirmedBookings: 0, totalRevenue: 0 });

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('No routes yet');
  });

  it('refresh re-fetches the report', async () => {
    await createComponent();
    const spy = vi.spyOn(reportService, 'getReport').mockReturnValue(of(report));

    component.refresh();

    expect(spy).toHaveBeenCalled();
  });
});

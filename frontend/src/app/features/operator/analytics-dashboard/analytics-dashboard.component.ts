import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { OperatorReportResponse } from '../../../core/models/report.model';
import { OperatorReportService } from '../../../core/services/operator-report.service';

/**
 * Self-contained analytics summary for the routes the caller (or their
 * partner, for a partner-affiliated operator) manages: confirmed bookings,
 * revenue, and seat occupancy per route, aggregated server-side from
 * GET /api/operator/reports so this stays a thin display layer.
 */
@Component({
  selector: 'tw-analytics-dashboard',
  imports: [DecimalPipe],
  templateUrl: './analytics-dashboard.component.html',
  styleUrl: './analytics-dashboard.component.scss',
})
export class AnalyticsDashboardComponent {
  private readonly reportService = inject(OperatorReportService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly loadError = signal(false);
  readonly report = signal<OperatorReportResponse | null>(null);

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.reportService
      .getReport()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (report) => this.report.set(report),
        error: () => this.loadError.set(true),
      });
  }

  routeLabel(route: { origin: string | null; destination: string | null; venue: string | null }): string {
    if (route.venue) {
      return route.venue;
    }
    return route.destination ? `${route.origin} → ${route.destination}` : (route.origin ?? '—');
  }

  occupancyPercent(rate: number): number {
    return Math.round(rate * 100);
  }
}

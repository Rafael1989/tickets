import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { RouteType } from '../../core/models/catalog.model';
import { RouteResponse } from '../../core/models/route.model';
import { NotificationService } from '../../core/services/notification.service';
import { RouteService } from '../../core/services/route.service';
import { FareMatrixComponent } from './fare-matrix/fare-matrix.component';
import { FleetManagerComponent } from './fleet-manager/fleet-manager.component';
import { ScheduleManagerComponent } from './schedule-manager/schedule-manager.component';

@Component({
  selector: 'tw-operator-portal',
  imports: [ReactiveFormsModule, ScheduleManagerComponent, FleetManagerComponent, FareMatrixComponent],
  templateUrl: './operator-portal.component.html',
  styleUrl: './operator-portal.component.scss',
})
export class OperatorPortalComponent {
  private readonly fb = inject(FormBuilder);
  private readonly routeService = inject(RouteService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly routes = signal<RouteResponse[]>([]);
  readonly loadingRoutes = signal(true);
  readonly savingRoute = signal(false);
  readonly editingRouteId = signal<number | null>(null);
  readonly managingSchedulesForRouteId = signal<number | null>(null);
  readonly managingFaresForRouteId = signal<number | null>(null);

  readonly routeForm = this.fb.nonNullable.group({
    type: this.fb.nonNullable.control<RouteType>('BUS'),
    origin: [''],
    destination: [''],
    venue: [''],
    durationMinutes: [60, [Validators.required, Validators.min(1)]],
  });

  constructor() {
    this.refreshRoutes();
  }

  private refreshRoutes(): void {
    this.loadingRoutes.set(true);
    this.routeService
      .listMyRoutes()
      .pipe(finalize(() => this.loadingRoutes.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (routes) => this.routes.set(routes) });
  }

  startEditRoute(route: RouteResponse): void {
    this.editingRouteId.set(route.id);
    this.routeForm.setValue({
      type: route.type,
      origin: route.origin ?? '',
      destination: route.destination ?? '',
      venue: route.venue ?? '',
      durationMinutes: route.durationMinutes ?? 60,
    });
  }

  cancelEditRoute(): void {
    this.editingRouteId.set(null);
    this.routeForm.reset({ type: 'BUS', origin: '', destination: '', venue: '', durationMinutes: 60 });
  }

  submitRoute(): void {
    if (this.routeForm.invalid || this.savingRoute()) {
      return;
    }
    const value = this.routeForm.getRawValue();
    const request = {
      type: value.type,
      origin: value.origin || null,
      destination: value.destination || null,
      venue: value.venue || null,
      durationMinutes: value.durationMinutes,
    };
    const editingId = this.editingRouteId();

    this.savingRoute.set(true);
    const request$ = editingId
      ? this.routeService.updateRoute(editingId, request)
      : this.routeService.createRoute(request);

    request$
      .pipe(finalize(() => this.savingRoute.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (route) => {
          this.routes.update((current) =>
            editingId ? current.map((r) => (r.id === route.id ? route : r)) : [...current, route],
          );
          this.notifications.success(editingId ? `Route #${route.id} updated.` : `Route #${route.id} created.`);
          this.cancelEditRoute();
        },
      });
  }

  toggleScheduleManager(routeId: number): void {
    this.managingSchedulesForRouteId.set(this.managingSchedulesForRouteId() === routeId ? null : routeId);
  }

  toggleFareMatrix(routeId: number): void {
    this.managingFaresForRouteId.set(this.managingFaresForRouteId() === routeId ? null : routeId);
  }
}

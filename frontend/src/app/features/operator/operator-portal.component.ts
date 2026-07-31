import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { RouteType, SeatResponse } from '../../core/models/catalog.model';
import { RouteResponse } from '../../core/models/route.model';
import { InventoryManagementService } from '../../core/services/inventory-management.service';
import { NotificationService } from '../../core/services/notification.service';
import { RouteService } from '../../core/services/route.service';
import { ScheduleService } from '../../core/services/schedule.service';

@Component({
  selector: 'tw-operator-portal',
  imports: [ReactiveFormsModule],
  templateUrl: './operator-portal.component.html',
  styleUrl: './operator-portal.component.scss',
})
export class OperatorPortalComponent {
  private readonly fb = inject(FormBuilder);
  private readonly routeService = inject(RouteService);
  private readonly inventoryService = inject(InventoryManagementService);
  private readonly scheduleService = inject(ScheduleService);
  private readonly notifications = inject(NotificationService);

  readonly routes = signal<RouteResponse[]>([]);
  readonly loadingRoutes = signal(true);
  readonly creatingRoute = signal(false);
  readonly creatingSchedule = signal(false);
  readonly addingSeat = signal(false);
  readonly viewedSeats = signal<SeatResponse[] | null>(null);
  readonly loadingSeats = signal(false);

  readonly routeForm = this.fb.nonNullable.group({
    type: this.fb.nonNullable.control<RouteType>('BUS'),
    origin: [''],
    destination: [''],
    venue: [''],
    durationMinutes: [60, [Validators.required, Validators.min(1)]],
  });

  readonly scheduleForm = this.fb.nonNullable.group({
    routeId: [null as number | null, Validators.required],
    departureTime: ['', Validators.required],
    arrivalTime: ['', Validators.required],
    baseFare: [0, [Validators.required, Validators.min(0)]],
    currency: ['USD', Validators.required],
  });

  readonly seatForm = this.fb.nonNullable.group({
    scheduleId: [null as number | null, Validators.required],
    seatNumber: ['', Validators.required],
    seatClass: ['economy', Validators.required],
    priceModifier: [1, [Validators.required, Validators.min(0)]],
  });

  readonly viewSeatsForm = this.fb.nonNullable.group({
    scheduleId: [null as number | null, Validators.required],
  });

  constructor() {
    this.refreshRoutes();
  }

  private refreshRoutes(): void {
    this.loadingRoutes.set(true);
    this.routeService
      .listMyRoutes()
      .pipe(finalize(() => this.loadingRoutes.set(false)))
      .subscribe({ next: (routes) => this.routes.set(routes) });
  }

  createRoute(): void {
    if (this.routeForm.invalid || this.creatingRoute()) {
      return;
    }
    const value = this.routeForm.getRawValue();

    this.creatingRoute.set(true);
    this.routeService
      .createRoute({
        type: value.type,
        origin: value.origin || null,
        destination: value.destination || null,
        venue: value.venue || null,
        durationMinutes: value.durationMinutes,
      })
      .pipe(finalize(() => this.creatingRoute.set(false)))
      .subscribe({
        next: (route) => {
          this.routes.update((current) => [...current, route]);
          this.notifications.success(`Route #${route.id} created.`);
        },
      });
  }

  createSchedule(): void {
    if (this.scheduleForm.invalid || this.creatingSchedule()) {
      return;
    }
    const value = this.scheduleForm.getRawValue();

    this.creatingSchedule.set(true);
    this.inventoryService
      .createSchedule({
        routeId: value.routeId!,
        departureTime: new Date(value.departureTime).toISOString(),
        arrivalTime: new Date(value.arrivalTime).toISOString(),
        baseFare: value.baseFare,
        currency: value.currency,
      })
      .pipe(finalize(() => this.creatingSchedule.set(false)))
      .subscribe({
        next: (schedule) => this.notifications.success(`Schedule #${schedule.id} created.`),
      });
  }

  addSeat(): void {
    if (this.seatForm.invalid || this.addingSeat()) {
      return;
    }
    const value = this.seatForm.getRawValue();

    this.addingSeat.set(true);
    this.inventoryService
      .addSeat({
        scheduleId: value.scheduleId!,
        seatNumber: value.seatNumber,
        seatClass: value.seatClass,
        priceModifier: value.priceModifier,
      })
      .pipe(finalize(() => this.addingSeat.set(false)))
      .subscribe({
        next: (seat) => this.notifications.success(`Seat ${seat.seatNumber} added.`),
      });
  }

  viewSeats(): void {
    const scheduleId = this.viewSeatsForm.getRawValue().scheduleId;
    if (!scheduleId) {
      return;
    }

    this.loadingSeats.set(true);
    this.scheduleService
      .getSeats(scheduleId)
      .pipe(finalize(() => this.loadingSeats.set(false)))
      .subscribe({ next: (seats) => this.viewedSeats.set(seats) });
  }
}

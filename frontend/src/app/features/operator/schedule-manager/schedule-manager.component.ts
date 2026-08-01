import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { ScheduleResponse, ScheduleStatus } from '../../../core/models/catalog.model';
import { DriverResponse, VehicleResponse } from '../../../core/models/route.model';
import { DriverService } from '../../../core/services/driver.service';
import { InventoryManagementService } from '../../../core/services/inventory-management.service';
import { NotificationService } from '../../../core/services/notification.service';
import { RouteService } from '../../../core/services/route.service';
import { VehicleService } from '../../../core/services/vehicle.service';
import { SeatGridEditorComponent } from '../seat-grid-editor/seat-grid-editor.component';

const SCHEDULE_STATUSES: ScheduleStatus[] = ['SCHEDULED', 'DELAYED', 'CANCELLED', 'COMPLETED'];

/** For a <input type="datetime-local"> value: an ISO instant, truncated to
 *  minutes, with no trailing "Z"/offset (the input's own format). */
function toLocalInputValue(iso: string): string {
  return iso.slice(0, 16);
}

@Component({
  selector: 'tw-schedule-manager',
  imports: [DatePipe, ReactiveFormsModule, SeatGridEditorComponent],
  templateUrl: './schedule-manager.component.html',
  styleUrl: './schedule-manager.component.scss',
})
export class ScheduleManagerComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly routeService = inject(RouteService);
  private readonly inventoryService = inject(InventoryManagementService);
  private readonly vehicleService = inject(VehicleService);
  private readonly driverService = inject(DriverService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly routeId = input.required<number>();

  readonly schedules = signal<ScheduleResponse[]>([]);
  readonly vehicles = signal<VehicleResponse[]>([]);
  readonly drivers = signal<DriverResponse[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly editingScheduleId = signal<number | null>(null);
  readonly managingSeatsForScheduleId = signal<number | null>(null);

  readonly scheduleStatuses = SCHEDULE_STATUSES;

  readonly scheduleForm = this.fb.nonNullable.group({
    departureTime: ['', Validators.required],
    arrivalTime: ['', Validators.required],
    baseFare: [0, [Validators.required, Validators.min(0)]],
    currency: ['USD', Validators.required],
    status: this.fb.nonNullable.control<ScheduleStatus>('SCHEDULED'),
    vehicleId: this.fb.control<number | null>(null),
    driverId: this.fb.control<number | null>(null),
  });

  ngOnInit(): void {
    this.loadSchedules();
    this.vehicleService
      .listMyVehicles()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (vehicles) => this.vehicles.set(vehicles) });
    this.driverService
      .listMyDrivers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (drivers) => this.drivers.set(drivers) });
  }

  private loadSchedules(): void {
    this.loading.set(true);
    this.routeService
      .listSchedulesForRoute(this.routeId())
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (schedules) => this.schedules.set(schedules) });
  }

  startEdit(schedule: ScheduleResponse): void {
    this.editingScheduleId.set(schedule.id);
    this.scheduleForm.setValue({
      departureTime: toLocalInputValue(schedule.departureTime),
      arrivalTime: toLocalInputValue(schedule.arrivalTime),
      baseFare: schedule.baseFare,
      currency: schedule.currency,
      status: schedule.status,
      vehicleId: schedule.vehicleId,
      driverId: schedule.driverId,
    });
  }

  cancelEdit(): void {
    this.editingScheduleId.set(null);
    this.scheduleForm.reset({
      departureTime: '',
      arrivalTime: '',
      baseFare: 0,
      currency: 'USD',
      status: 'SCHEDULED',
      vehicleId: null,
      driverId: null,
    });
  }

  submitSchedule(): void {
    if (this.scheduleForm.invalid || this.saving()) {
      return;
    }
    const value = this.scheduleForm.getRawValue();
    const request = {
      routeId: this.routeId(),
      departureTime: new Date(value.departureTime).toISOString(),
      arrivalTime: new Date(value.arrivalTime).toISOString(),
      baseFare: value.baseFare,
      currency: value.currency,
      status: value.status,
      vehicleId: value.vehicleId,
      driverId: value.driverId,
    };
    const editingId = this.editingScheduleId();

    this.saving.set(true);
    const request$ = editingId
      ? this.inventoryService.updateSchedule(editingId, request)
      : this.inventoryService.createSchedule(request);

    request$
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (schedule) => {
          this.schedules.update((current) =>
            editingId
              ? current.map((s) => (s.id === schedule.id ? schedule : s))
              : [...current, schedule],
          );
          this.notifications.success(editingId ? `Schedule #${schedule.id} updated.` : `Schedule #${schedule.id} created.`);
          this.cancelEdit();
        },
      });
  }

  toggleSeatManager(scheduleId: number): void {
    this.managingSeatsForScheduleId.set(this.managingSeatsForScheduleId() === scheduleId ? null : scheduleId);
  }
}

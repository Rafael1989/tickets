import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { RouteType } from '../../../core/models/catalog.model';
import { DriverResponse, VehicleResponse } from '../../../core/models/route.model';
import { DriverService } from '../../../core/services/driver.service';
import { NotificationService } from '../../../core/services/notification.service';
import { VehicleService } from '../../../core/services/vehicle.service';

/**
 * Vehicles and drivers are operator-global (not scoped to a single route),
 * unlike schedules/seats/fare rules - so this sits alongside the route list
 * in operator-portal rather than nested under a route, and both lists are
 * re-fetched by schedule-manager to populate its assignment dropdowns.
 */
@Component({
  selector: 'tw-fleet-manager',
  imports: [ReactiveFormsModule],
  templateUrl: './fleet-manager.component.html',
  styleUrl: './fleet-manager.component.scss',
})
export class FleetManagerComponent {
  private readonly fb = inject(FormBuilder);
  private readonly vehicleService = inject(VehicleService);
  private readonly driverService = inject(DriverService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly vehicles = signal<VehicleResponse[]>([]);
  readonly drivers = signal<DriverResponse[]>([]);
  readonly loadingVehicles = signal(true);
  readonly loadingDrivers = signal(true);
  readonly savingVehicle = signal(false);
  readonly savingDriver = signal(false);

  readonly vehicleForm = this.fb.nonNullable.group({
    type: this.fb.nonNullable.control<RouteType>('BUS'),
    identifier: ['', Validators.required],
    capacity: [40, [Validators.required, Validators.min(1)]],
    model: [''],
  });

  readonly driverForm = this.fb.nonNullable.group({
    fullName: ['', Validators.required],
    licenseNumber: ['', Validators.required],
  });

  constructor() {
    this.refreshVehicles();
    this.refreshDrivers();
  }

  private refreshVehicles(): void {
    this.loadingVehicles.set(true);
    this.vehicleService
      .listMyVehicles()
      .pipe(finalize(() => this.loadingVehicles.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (vehicles) => this.vehicles.set(vehicles) });
  }

  private refreshDrivers(): void {
    this.loadingDrivers.set(true);
    this.driverService
      .listMyDrivers()
      .pipe(finalize(() => this.loadingDrivers.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (drivers) => this.drivers.set(drivers) });
  }

  createVehicle(): void {
    if (this.vehicleForm.invalid || this.savingVehicle()) {
      return;
    }
    const value = this.vehicleForm.getRawValue();

    this.savingVehicle.set(true);
    this.vehicleService
      .createVehicle({ type: value.type, identifier: value.identifier, capacity: value.capacity, model: value.model || null })
      .pipe(finalize(() => this.savingVehicle.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (vehicle) => {
          this.vehicles.update((current) => [...current, vehicle]);
          this.vehicleForm.reset({ type: 'BUS', identifier: '', capacity: 40, model: '' });
          this.notifications.success(`Vehicle ${vehicle.identifier} added.`);
        },
      });
  }

  createDriver(): void {
    if (this.driverForm.invalid || this.savingDriver()) {
      return;
    }
    const value = this.driverForm.getRawValue();

    this.savingDriver.set(true);
    this.driverService
      .createDriver(value)
      .pipe(finalize(() => this.savingDriver.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (driver) => {
          this.drivers.update((current) => [...current, driver]);
          this.driverForm.reset({ fullName: '', licenseNumber: '' });
          this.notifications.success(`Driver ${driver.fullName} added.`);
        },
      });
  }
}

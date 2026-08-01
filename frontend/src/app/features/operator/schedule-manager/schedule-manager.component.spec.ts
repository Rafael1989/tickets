import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ScheduleResponse } from '../../../core/models/catalog.model';
import { DriverService } from '../../../core/services/driver.service';
import { InventoryManagementService } from '../../../core/services/inventory-management.service';
import { NotificationService } from '../../../core/services/notification.service';
import { RouteService } from '../../../core/services/route.service';
import { VehicleService } from '../../../core/services/vehicle.service';
import { ScheduleManagerComponent } from './schedule-manager.component';

describe('ScheduleManagerComponent', () => {
  let fixture: ComponentFixture<ScheduleManagerComponent>;
  let component: ScheduleManagerComponent;
  let routeService: RouteService;
  let inventoryService: InventoryManagementService;
  let notifications: NotificationService;

  const existingSchedule: ScheduleResponse = {
    id: 10,
    routeId: 1,
    departureTime: '2026-08-10T10:00:00.000Z',
    arrivalTime: '2026-08-10T12:00:00.000Z',
    baseFare: 50,
    currency: 'USD',
    status: 'SCHEDULED',
    vehicleId: null,
    driverId: null,
  };

  async function createComponent() {
    await TestBed.configureTestingModule({
      imports: [ScheduleManagerComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    routeService = TestBed.inject(RouteService);
    vi.spyOn(routeService, 'listSchedulesForRoute').mockReturnValue(of([existingSchedule]));
    inventoryService = TestBed.inject(InventoryManagementService);
    notifications = TestBed.inject(NotificationService);
    vi.spyOn(TestBed.inject(VehicleService), 'listMyVehicles').mockReturnValue(of([]));
    vi.spyOn(TestBed.inject(DriverService), 'listMyDrivers').mockReturnValue(of([]));

    fixture = TestBed.createComponent(ScheduleManagerComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('routeId', 1);
    fixture.detectChanges();
  }

  it('loads schedules for the given route on init', async () => {
    await createComponent();

    expect(routeService.listSchedulesForRoute).toHaveBeenCalledWith(1);
    expect(component.schedules()).toEqual([existingSchedule]);
  });

  it('submitSchedule creates a new schedule when not editing', async () => {
    await createComponent();
    component.scheduleForm.setValue({
      departureTime: '2026-08-11T10:00',
      arrivalTime: '2026-08-11T12:00',
      baseFare: 60,
      currency: 'EUR',
      status: 'SCHEDULED',
      vehicleId: null,
      driverId: null,
    });
    const created: ScheduleResponse = { ...existingSchedule, id: 20, baseFare: 60, currency: 'EUR' };
    vi.spyOn(inventoryService, 'createSchedule').mockReturnValue(of(created));
    const successSpy = vi.spyOn(notifications, 'success');

    component.submitSchedule();

    expect(inventoryService.createSchedule).toHaveBeenCalledWith(
      expect.objectContaining({ routeId: 1, baseFare: 60, currency: 'EUR' }),
    );
    expect(component.schedules()).toEqual([existingSchedule, created]);
    expect(successSpy).toHaveBeenCalledWith('Schedule #20 created.');
  });

  it('startEdit populates the form and submitSchedule then calls updateSchedule', async () => {
    await createComponent();

    component.startEdit(existingSchedule);
    expect(component.editingScheduleId()).toBe(10);
    expect(component.scheduleForm.getRawValue().baseFare).toBe(50);

    const updated: ScheduleResponse = { ...existingSchedule, baseFare: 75 };
    component.scheduleForm.patchValue({ baseFare: 75 });
    vi.spyOn(inventoryService, 'updateSchedule').mockReturnValue(of(updated));

    component.submitSchedule();

    expect(inventoryService.updateSchedule).toHaveBeenCalledWith(10, expect.objectContaining({ baseFare: 75 }));
    expect(component.schedules()).toEqual([updated]);
    expect(component.editingScheduleId()).toBeNull();
  });

  it('cancelEdit clears the editing state and resets the form', async () => {
    await createComponent();
    component.startEdit(existingSchedule);

    component.cancelEdit();

    expect(component.editingScheduleId()).toBeNull();
    expect(component.scheduleForm.getRawValue().baseFare).toBe(0);
  });

  it('toggleSeatManager toggles which schedule is showing its seat grid', async () => {
    await createComponent();

    component.toggleSeatManager(10);
    expect(component.managingSeatsForScheduleId()).toBe(10);

    component.toggleSeatManager(10);
    expect(component.managingSeatsForScheduleId()).toBeNull();
  });
});

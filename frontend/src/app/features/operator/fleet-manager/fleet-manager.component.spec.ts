import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { DriverResponse, VehicleResponse } from '../../../core/models/route.model';
import { DriverService } from '../../../core/services/driver.service';
import { NotificationService } from '../../../core/services/notification.service';
import { VehicleService } from '../../../core/services/vehicle.service';
import { FleetManagerComponent } from './fleet-manager.component';

describe('FleetManagerComponent', () => {
  let fixture: ComponentFixture<FleetManagerComponent>;
  let component: FleetManagerComponent;
  let vehicleService: VehicleService;
  let driverService: DriverService;
  let notifications: NotificationService;

  const existingVehicle: VehicleResponse = { id: 5, operatorId: 1, type: 'BUS', identifier: 'BUS-1234', capacity: 45, model: null };
  const existingDriver: DriverResponse = { id: 7, operatorId: 1, fullName: 'Jane Doe', licenseNumber: 'LIC-123' };

  async function createComponent() {
    await TestBed.configureTestingModule({
      imports: [FleetManagerComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    vehicleService = TestBed.inject(VehicleService);
    vi.spyOn(vehicleService, 'listMyVehicles').mockReturnValue(of([existingVehicle]));
    driverService = TestBed.inject(DriverService);
    vi.spyOn(driverService, 'listMyDrivers').mockReturnValue(of([existingDriver]));
    notifications = TestBed.inject(NotificationService);

    fixture = TestBed.createComponent(FleetManagerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => await createComponent());

  it('loads vehicles and drivers on init', () => {
    expect(component.vehicles()).toEqual([existingVehicle]);
    expect(component.drivers()).toEqual([existingDriver]);
  });

  it('createVehicle does nothing while the form is invalid', () => {
    const createSpy = vi.spyOn(vehicleService, 'createVehicle');
    component.vehicleForm.patchValue({ identifier: '' });

    component.createVehicle();

    expect(createSpy).not.toHaveBeenCalled();
  });

  it('createVehicle submits the form and appends the new vehicle', () => {
    const created: VehicleResponse = { ...existingVehicle, id: 6, identifier: 'BUS-5678' };
    vi.spyOn(vehicleService, 'createVehicle').mockReturnValue(of(created));
    const successSpy = vi.spyOn(notifications, 'success');
    component.vehicleForm.setValue({ type: 'BUS', identifier: 'BUS-5678', capacity: 40, model: '' });

    component.createVehicle();

    expect(vehicleService.createVehicle).toHaveBeenCalledWith({
      type: 'BUS',
      identifier: 'BUS-5678',
      capacity: 40,
      model: null,
    });
    expect(component.vehicles()).toEqual([existingVehicle, created]);
    expect(successSpy).toHaveBeenCalledWith('Vehicle BUS-5678 added.');
  });

  it('createDriver does nothing while the form is invalid', () => {
    const createSpy = vi.spyOn(driverService, 'createDriver');
    component.driverForm.patchValue({ fullName: '' });

    component.createDriver();

    expect(createSpy).not.toHaveBeenCalled();
  });

  it('createDriver submits the form and appends the new driver', () => {
    const created: DriverResponse = { ...existingDriver, id: 8, fullName: 'John Roe' };
    vi.spyOn(driverService, 'createDriver').mockReturnValue(of(created));
    component.driverForm.setValue({ fullName: 'John Roe', licenseNumber: 'LIC-999' });

    component.createDriver();

    expect(driverService.createDriver).toHaveBeenCalledWith({ fullName: 'John Roe', licenseNumber: 'LIC-999' });
    expect(component.drivers()).toEqual([existingDriver, created]);
  });
});

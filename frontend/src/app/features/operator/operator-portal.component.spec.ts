import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { RouteResponse } from '../../core/models/route.model';
import { DriverService } from '../../core/services/driver.service';
import { NotificationService } from '../../core/services/notification.service';
import { RouteService } from '../../core/services/route.service';
import { VehicleService } from '../../core/services/vehicle.service';
import { OperatorPortalComponent } from './operator-portal.component';

describe('OperatorPortalComponent', () => {
  let fixture: ComponentFixture<OperatorPortalComponent>;
  let component: OperatorPortalComponent;
  let routeService: RouteService;
  let notifications: NotificationService;

  const existingRoute: RouteResponse = {
    id: 1,
    operatorId: 9,
    type: 'BUS',
    origin: 'NYC',
    destination: 'Boston',
    venue: null,
    durationMinutes: 240,
  };

  async function createComponent() {
    await TestBed.configureTestingModule({
      imports: [OperatorPortalComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    routeService = TestBed.inject(RouteService);
    vi.spyOn(routeService, 'listMyRoutes').mockReturnValue(of([existingRoute]));
    vi.spyOn(TestBed.inject(VehicleService), 'listMyVehicles').mockReturnValue(of([]));
    vi.spyOn(TestBed.inject(DriverService), 'listMyDrivers').mockReturnValue(of([]));

    notifications = TestBed.inject(NotificationService);

    fixture = TestBed.createComponent(OperatorPortalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => await createComponent());

  it("loads the operator's routes on init", () => {
    expect(component.loadingRoutes()).toBe(false);
    expect(component.routes()).toEqual([existingRoute]);
  });

  it('submitRoute does nothing while the form is invalid', () => {
    const createSpy = vi.spyOn(routeService, 'createRoute');
    component.routeForm.patchValue({ durationMinutes: 0 });

    component.submitRoute();

    expect(createSpy).not.toHaveBeenCalled();
  });

  it('submitRoute creates a route and appends it to the list when not editing', () => {
    const created: RouteResponse = { ...existingRoute, id: 2, destination: 'Miami' };
    vi.spyOn(routeService, 'createRoute').mockReturnValue(of(created));
    const successSpy = vi.spyOn(notifications, 'success');
    component.routeForm.patchValue({
      type: 'BUS',
      origin: 'NYC',
      destination: 'Miami',
      durationMinutes: 300,
    });

    component.submitRoute();

    expect(routeService.createRoute).toHaveBeenCalledWith({
      type: 'BUS',
      origin: 'NYC',
      destination: 'Miami',
      venue: null,
      durationMinutes: 300,
    });
    expect(component.routes()).toEqual([existingRoute, created]);
    expect(successSpy).toHaveBeenCalledWith('Route #2 created.');
  });

  it('startEditRoute populates the form and submitRoute then calls updateRoute', () => {
    component.startEditRoute(existingRoute);
    expect(component.editingRouteId()).toBe(1);
    expect(component.routeForm.getRawValue().destination).toBe('Boston');

    const updated: RouteResponse = { ...existingRoute, destination: 'Philadelphia' };
    component.routeForm.patchValue({ destination: 'Philadelphia' });
    vi.spyOn(routeService, 'updateRoute').mockReturnValue(of(updated));

    component.submitRoute();

    expect(routeService.updateRoute).toHaveBeenCalledWith(1, expect.objectContaining({ destination: 'Philadelphia' }));
    expect(component.routes()).toEqual([updated]);
    expect(component.editingRouteId()).toBeNull();
  });

  it('cancelEditRoute clears the editing state and resets the form', () => {
    component.startEditRoute(existingRoute);

    component.cancelEditRoute();

    expect(component.editingRouteId()).toBeNull();
    expect(component.routeForm.getRawValue().destination).toBe('');
  });

  it('toggleScheduleManager toggles which route is showing its schedule manager', () => {
    component.toggleScheduleManager(1);
    expect(component.managingSchedulesForRouteId()).toBe(1);

    component.toggleScheduleManager(1);
    expect(component.managingSchedulesForRouteId()).toBeNull();
  });

  it('toggleFareMatrix toggles which route is showing its fare matrix', () => {
    component.toggleFareMatrix(1);
    expect(component.managingFaresForRouteId()).toBe(1);

    component.toggleFareMatrix(1);
    expect(component.managingFaresForRouteId()).toBeNull();
  });
});

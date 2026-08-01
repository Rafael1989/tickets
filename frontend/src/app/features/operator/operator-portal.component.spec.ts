import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { SeatResponse } from '../../core/models/catalog.model';
import { RouteResponse, ScheduleRequest } from '../../core/models/route.model';
import { InventoryManagementService } from '../../core/services/inventory-management.service';
import { NotificationService } from '../../core/services/notification.service';
import { RouteService } from '../../core/services/route.service';
import { ScheduleService } from '../../core/services/schedule.service';
import { OperatorPortalComponent } from './operator-portal.component';

describe('OperatorPortalComponent', () => {
  let fixture: ComponentFixture<OperatorPortalComponent>;
  let component: OperatorPortalComponent;
  let routeService: RouteService;
  let inventoryService: InventoryManagementService;
  let scheduleService: ScheduleService;
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

    inventoryService = TestBed.inject(InventoryManagementService);
    scheduleService = TestBed.inject(ScheduleService);
    notifications = TestBed.inject(NotificationService);

    fixture = TestBed.createComponent(OperatorPortalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => await createComponent());

  it('loads the operator\'s routes on init', () => {
    expect(component.loadingRoutes()).toBe(false);
    expect(component.routes()).toEqual([existingRoute]);
  });

  it('createRoute does nothing while the form is invalid', () => {
    const createSpy = vi.spyOn(routeService, 'createRoute');
    component.routeForm.patchValue({ durationMinutes: 0 });

    component.createRoute();

    expect(createSpy).not.toHaveBeenCalled();
  });

  it('createRoute submits the form and appends the new route to the list', () => {
    const created: RouteResponse = { ...existingRoute, id: 2, destination: 'Miami' };
    vi.spyOn(routeService, 'createRoute').mockReturnValue(of(created));
    const successSpy = vi.spyOn(notifications, 'success');
    component.routeForm.patchValue({
      type: 'BUS',
      origin: 'NYC',
      destination: 'Miami',
      durationMinutes: 300,
    });

    component.createRoute();

    expect(routeService.createRoute).toHaveBeenCalledWith({
      type: 'BUS',
      origin: 'NYC',
      destination: 'Miami',
      venue: null,
      durationMinutes: 300,
    });
    expect(component.routes()).toEqual([existingRoute, created]);
    expect(successSpy).toHaveBeenCalledWith("Route #2 created.");
  });

  it('createSchedule does nothing while the form is invalid', () => {
    const createSpy = vi.spyOn(inventoryService, 'createSchedule');

    component.createSchedule();

    expect(createSpy).not.toHaveBeenCalled();
  });

  it('createSchedule submits schedule details for the selected route', () => {
    vi.spyOn(inventoryService, 'createSchedule').mockReturnValue(
      of({
        id: 10,
        routeId: 1,
        departureTime: '2026-08-01T10:00:00.000Z',
        arrivalTime: '2026-08-01T12:00:00.000Z',
        baseFare: 50,
        currency: 'USD',
        status: 'SCHEDULED',
      }),
    );
    component.scheduleForm.setValue({
      routeId: 1,
      departureTime: '2026-08-01T10:00',
      arrivalTime: '2026-08-01T12:00',
      baseFare: 50,
      currency: 'USD',
    });

    component.createSchedule();

    expect(inventoryService.createSchedule).toHaveBeenCalled();
    const request = (inventoryService.createSchedule as ReturnType<typeof vi.fn>).mock
      .calls[0][0] as ScheduleRequest;
    expect(request.routeId).toBe(1);
    expect(request.baseFare).toBe(50);
    expect(request.currency).toBe('USD');
  });

  it('addSeat does nothing while the form is invalid', () => {
    const addSpy = vi.spyOn(inventoryService, 'addSeat');

    component.addSeat();

    expect(addSpy).not.toHaveBeenCalled();
  });

  it('addSeat submits the seat details', () => {
    vi.spyOn(inventoryService, 'addSeat').mockReturnValue(
      of({
        id: 5,
        scheduleId: 1,
        seatNumber: '2B',
        seatClass: 'economy',
        status: 'AVAILABLE',
        priceModifier: 1,
        estimatedFare: 20,
        heldUntil: null,
        heldByMe: false,
      }),
    );
    component.seatForm.setValue({
      scheduleId: 1,
      seatNumber: '2B',
      seatClass: 'economy',
      priceModifier: 1,
    });

    component.addSeat();

    expect(inventoryService.addSeat).toHaveBeenCalledWith({
      scheduleId: 1,
      seatNumber: '2B',
      seatClass: 'economy',
      priceModifier: 1,
    });
  });

  it('viewSeats loads and stores the seats for a given schedule id', () => {
    const seats: SeatResponse[] = [
      {
        id: 1,
        scheduleId: 1,
        seatNumber: '1A',
        seatClass: 'economy',
        status: 'AVAILABLE',
        priceModifier: 1,
        estimatedFare: 20,
        heldUntil: null,
        heldByMe: false,
      },
    ];
    vi.spyOn(scheduleService, 'getSeats').mockReturnValue(of(seats));
    component.viewSeatsForm.setValue({ scheduleId: 1 });

    component.viewSeats();

    expect(scheduleService.getSeats).toHaveBeenCalledWith(1);
    expect(component.viewedSeats()).toEqual(seats);
  });

  it('viewSeats does nothing without a schedule id', () => {
    const getSeatsSpy = vi.spyOn(scheduleService, 'getSeats');

    component.viewSeats();

    expect(getSeatsSpy).not.toHaveBeenCalled();
  });
});

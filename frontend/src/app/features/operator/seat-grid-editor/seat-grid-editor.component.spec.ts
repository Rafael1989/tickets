import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SeatResponse } from '../../../core/models/catalog.model';
import { InventoryManagementService } from '../../../core/services/inventory-management.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ScheduleService } from '../../../core/services/schedule.service';
import { SeatGridEditorComponent } from './seat-grid-editor.component';

describe('SeatGridEditorComponent', () => {
  let fixture: ComponentFixture<SeatGridEditorComponent>;
  let component: SeatGridEditorComponent;
  let scheduleService: ScheduleService;
  let inventoryService: InventoryManagementService;
  let notifications: NotificationService;

  function seat(overrides: Partial<SeatResponse> = {}): SeatResponse {
    return {
      id: 1,
      scheduleId: 1,
      seatNumber: '12A',
      seatClass: 'economy',
      status: 'AVAILABLE',
      priceModifier: 1,
      estimatedFare: 20,
      heldUntil: null,
      heldByMe: false,
      ...overrides,
    };
  }

  async function createComponent(seats: SeatResponse[]) {
    await TestBed.configureTestingModule({
      imports: [SeatGridEditorComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    scheduleService = TestBed.inject(ScheduleService);
    vi.spyOn(scheduleService, 'getSeats').mockReturnValue(of(seats));
    inventoryService = TestBed.inject(InventoryManagementService);
    notifications = TestBed.inject(NotificationService);

    fixture = TestBed.createComponent(SeatGridEditorComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('scheduleId', 1);
    fixture.detectChanges();
  }

  it('loads seats for the given schedule on init', async () => {
    const seats = [seat()];
    await createComponent(seats);

    expect(scheduleService.getSeats).toHaveBeenCalledWith(1);
    expect(component.seats()).toEqual(seats);
  });

  it('selectSeat toggles selection and populates the edit form', async () => {
    const s = seat({ status: 'BLOCKED', priceModifier: 1.5 });
    await createComponent([s]);

    component.selectSeat(s);
    expect(component.selectedSeatId()).toBe(1);
    expect(component.editForm.getRawValue()).toEqual({ status: 'BLOCKED', priceModifier: 1.5 });

    component.selectSeat(s);
    expect(component.selectedSeatId()).toBeNull();
  });

  it('flags a BOOKED seat as locked with an explanatory message', async () => {
    const s = seat({ status: 'BOOKED' });
    await createComponent([s]);

    component.selectSeat(s);

    expect(component.selectedSeatLocked()).toContain('booked');
  });

  it('flags an actively HELD seat as locked, but not an expired one', async () => {
    const activelyHeld = seat({ id: 1, status: 'HELD', heldUntil: new Date(Date.now() + 60_000).toISOString() });
    const expiredHold = seat({ id: 2, status: 'HELD', heldUntil: new Date(Date.now() - 60_000).toISOString() });
    await createComponent([activelyHeld, expiredHold]);

    component.selectSeat(activelyHeld);
    expect(component.selectedSeatLocked()).toContain('mid-checkout');

    component.selectSeat(activelyHeld);
    component.selectSeat(expiredHold);
    expect(component.selectedSeatLocked()).toBeNull();
  });

  it('saveSeatEdit updates the seat and merges the result', async () => {
    const s = seat();
    await createComponent([s]);
    component.selectSeat(s);
    component.editForm.setValue({ status: 'BLOCKED', priceModifier: 2 });
    const updated: SeatResponse = { ...s, status: 'BLOCKED', priceModifier: 2 };
    vi.spyOn(inventoryService, 'updateSeat').mockReturnValue(of(updated));
    const successSpy = vi.spyOn(notifications, 'success');

    component.saveSeatEdit();

    expect(inventoryService.updateSeat).toHaveBeenCalledWith(1, { status: 'BLOCKED', priceModifier: 2 });
    expect(component.seats()[0]).toEqual(updated);
    expect(successSpy).toHaveBeenCalled();
  });

  it('saveSeatEdit does nothing for a locked seat', async () => {
    const s = seat({ status: 'BOOKED' });
    await createComponent([s]);
    component.selectSeat(s);
    const updateSpy = vi.spyOn(inventoryService, 'updateSeat');

    component.saveSeatEdit();

    expect(updateSpy).not.toHaveBeenCalled();
  });

  it('addSeat submits the new seat and appends it to the list', async () => {
    await createComponent([]);
    component.addSeatForm.setValue({ seatNumber: '13A', seatClass: 'economy', priceModifier: 1 });
    const added = seat({ id: 2, seatNumber: '13A' });
    vi.spyOn(inventoryService, 'addSeat').mockReturnValue(of(added));

    component.addSeat();

    expect(inventoryService.addSeat).toHaveBeenCalledWith({
      scheduleId: 1,
      seatNumber: '13A',
      seatClass: 'economy',
      priceModifier: 1,
    });
    expect(component.seats()).toEqual([added]);
  });

  it('rows() derives unique row prefixes from seat numbers, sorted numerically', async () => {
    await createComponent([seat({ id: 1, seatNumber: '2A' }), seat({ id: 2, seatNumber: '10A' }), seat({ id: 3, seatNumber: '2B' })]);

    expect(component.rows()).toEqual(['2', '10']);
  });

  it('blockRow updates every non-BOOKED seat in the row and reports partial success', async () => {
    const seats = [
      seat({ id: 1, seatNumber: '2A', status: 'AVAILABLE' }),
      seat({ id: 2, seatNumber: '2B', status: 'BOOKED' }),
      seat({ id: 3, seatNumber: '2C', status: 'AVAILABLE' }),
    ];
    await createComponent(seats);
    component.onRowSelected({ target: { value: '2' } } as unknown as Event);
    vi.spyOn(inventoryService, 'updateSeat').mockImplementation((seatId) =>
      of({ ...seats.find((s) => s.id === seatId)!, status: 'BLOCKED' }),
    );
    const successSpy = vi.spyOn(notifications, 'success');

    component.blockRow();

    // Row 2 has 3 seats, one BOOKED (skipped up front) -> only 2 seats targeted, both succeed.
    expect(inventoryService.updateSeat).toHaveBeenCalledTimes(2);
    expect(successSpy).toHaveBeenCalledWith(expect.stringContaining('Updated 2 seat(s)'));
  });

  it('blockRow reports seats that fail mid-batch without blocking the successful ones', async () => {
    const seats = [
      seat({ id: 1, seatNumber: '3A', status: 'AVAILABLE' }),
      seat({ id: 2, seatNumber: '3B', status: 'AVAILABLE' }),
    ];
    await createComponent(seats);
    component.onRowSelected({ target: { value: '3' } } as unknown as Event);
    vi.spyOn(inventoryService, 'updateSeat').mockImplementation((seatId) =>
      seatId === 1
        ? of({ ...seats[0], status: 'BLOCKED' })
        : throwError(() => new Error('conflict')),
    );
    const successSpy = vi.spyOn(notifications, 'success');

    component.blockRow();

    expect(successSpy).toHaveBeenCalledWith(expect.stringContaining("1 couldn't be changed"));
  });
});

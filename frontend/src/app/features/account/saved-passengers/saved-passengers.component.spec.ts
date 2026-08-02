import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { PassengerResponse } from '../../../core/models/passenger.model';
import { NotificationService } from '../../../core/services/notification.service';
import { PassengerService } from '../../../core/services/passenger.service';
import { SavedPassengersComponent } from './saved-passengers.component';

describe('SavedPassengersComponent', () => {
  let fixture: ComponentFixture<SavedPassengersComponent>;
  let component: SavedPassengersComponent;
  let passengerService: PassengerService;
  let notifications: NotificationService;

  const passenger: PassengerResponse = {
    id: 1,
    userId: 1,
    fullName: 'Jane Doe',
    dob: '1990-01-01',
    idType: 'passport',
    idNumber: 'X123456',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SavedPassengersComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(SavedPassengersComponent);
    component = fixture.componentInstance;
    passengerService = TestBed.inject(PassengerService);
    notifications = TestBed.inject(NotificationService);
  });

  it('renders the empty state when there are no saved passengers', () => {
    vi.spyOn(passengerService, 'listMyPassengers').mockReturnValue(of([]));
    fixture.detectChanges();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('No saved passengers yet');
  });

  it('renders a card per saved passenger', () => {
    vi.spyOn(passengerService, 'listMyPassengers').mockReturnValue(of([passenger]));
    fixture.detectChanges();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('Jane Doe');
    expect(html).toContain('X123456');
  });

  it('opens the add-passenger drawer with a blank form', () => {
    vi.spyOn(passengerService, 'listMyPassengers').mockReturnValue(of([]));
    fixture.detectChanges();

    component.openAddForm();

    expect(component.formOpen()).toBe(true);
    expect(component.editingId()).toBeNull();
    expect(component.form.value.fullName).toBe('');
  });

  it('opens the edit drawer pre-filled with the passenger being edited', () => {
    vi.spyOn(passengerService, 'listMyPassengers').mockReturnValue(of([passenger]));
    fixture.detectChanges();

    component.openEditForm(passenger);

    expect(component.formOpen()).toBe(true);
    expect(component.editingId()).toBe(1);
    expect(component.form.value.fullName).toBe('Jane Doe');
  });

  it('creates a new passenger and appends it to the list', () => {
    vi.spyOn(passengerService, 'listMyPassengers').mockReturnValue(of([]));
    const createSpy = vi.spyOn(passengerService, 'createPassenger').mockReturnValue(of(passenger));
    const toastSpy = vi.spyOn(notifications, 'success');
    fixture.detectChanges();

    component.openAddForm();
    component.form.setValue({ fullName: 'Jane Doe', dob: '1990-01-01', idType: 'passport', idNumber: 'X123456' });
    component.submit();

    expect(createSpy).toHaveBeenCalled();
    expect(component.passengers()).toEqual([passenger]);
    expect(component.formOpen()).toBe(false);
    expect(toastSpy).toHaveBeenCalledWith('Passenger added.');
  });

  it('updates an existing passenger in place', () => {
    vi.spyOn(passengerService, 'listMyPassengers').mockReturnValue(of([passenger]));
    const updated = { ...passenger, fullName: 'Jane Updated' };
    const updateSpy = vi.spyOn(passengerService, 'updatePassenger').mockReturnValue(of(updated));
    fixture.detectChanges();

    component.openEditForm(passenger);
    component.form.patchValue({ fullName: 'Jane Updated' });
    component.submit();

    expect(updateSpy).toHaveBeenCalledWith(1, expect.objectContaining({ fullName: 'Jane Updated' }));
    expect(component.passengers()).toEqual([updated]);
  });

  it('does not submit and shows a format error for an ID number that does not match the selected ID type', () => {
    vi.spyOn(passengerService, 'listMyPassengers').mockReturnValue(of([]));
    const createSpy = vi.spyOn(passengerService, 'createPassenger');
    fixture.detectChanges();

    component.openAddForm();
    component.form.setValue({ fullName: 'Alex Guest', dob: '1990-01-01', idType: 'passport', idNumber: 'A1' });
    component.form.controls.idNumber.markAsTouched();
    component.submit();
    fixture.detectChanges();

    expect(createSpy).not.toHaveBeenCalled();
    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain("Doesn't look like a valid ID number");
  });

  it('does not submit an invalid form', () => {
    vi.spyOn(passengerService, 'listMyPassengers').mockReturnValue(of([]));
    const createSpy = vi.spyOn(passengerService, 'createPassenger');
    fixture.detectChanges();

    component.openAddForm();
    component.form.patchValue({ fullName: '' });
    component.submit();

    expect(createSpy).not.toHaveBeenCalled();
  });

  it('deletes a passenger after confirmation and removes it from the list', () => {
    vi.spyOn(passengerService, 'listMyPassengers').mockReturnValue(of([passenger]));
    const deleteSpy = vi.spyOn(passengerService, 'deletePassenger').mockReturnValue(of(undefined));
    fixture.detectChanges();

    component.confirmDelete(1);
    expect(component.confirmingDeleteId()).toBe(1);

    component.deletePassenger(1);

    expect(deleteSpy).toHaveBeenCalledWith(1);
    expect(component.passengers()).toEqual([]);
    expect(component.confirmingDeleteId()).toBeNull();
  });

  it('cancelDelete clears the pending confirmation without deleting', () => {
    vi.spyOn(passengerService, 'listMyPassengers').mockReturnValue(of([passenger]));
    const deleteSpy = vi.spyOn(passengerService, 'deletePassenger');
    fixture.detectChanges();

    component.confirmDelete(1);
    component.cancelDelete();

    expect(component.confirmingDeleteId()).toBeNull();
    expect(deleteSpy).not.toHaveBeenCalled();
  });
});

import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { PassengerResponse } from '../../../core/models/passenger.model';
import { NotificationService } from '../../../core/services/notification.service';
import { PassengerService } from '../../../core/services/passenger.service';
import { idNumberValidator } from '../../../core/validators/id-number.validator';

@Component({
  selector: 'tw-saved-passengers',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './saved-passengers.component.html',
  styleUrl: './saved-passengers.component.scss',
})
export class SavedPassengersComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly passengerService = inject(PassengerService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly idTypes = [
    { value: 'passport', label: 'Passport' },
    { value: 'national_id', label: 'National ID' },
    { value: 'driver_license', label: "Driver's license" },
  ];
  readonly today = new Date().toISOString().slice(0, 10);

  readonly loading = signal(true);
  readonly passengers = signal<PassengerResponse[]>([]);
  readonly formOpen = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly submitting = signal(false);
  readonly confirmingDeleteId = signal<number | null>(null);
  readonly deletingId = signal<number | null>(null);

  readonly form = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(150)]],
    dob: ['', Validators.required],
    idType: ['passport', Validators.required],
    idNumber: ['', [Validators.required, Validators.maxLength(50), idNumberValidator()]],
  });

  ngOnInit(): void {
    this.passengerService
      .listMyPassengers()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (passengers) => this.passengers.set(passengers),
      });

    this.form.controls.idType.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.form.controls.idNumber.updateValueAndValidity());
  }

  openAddForm(): void {
    this.editingId.set(null);
    this.form.reset({ fullName: '', dob: '', idType: 'passport', idNumber: '' });
    this.formOpen.set(true);
  }

  openEditForm(passenger: PassengerResponse): void {
    this.editingId.set(passenger.id);
    this.form.reset({
      fullName: passenger.fullName,
      dob: passenger.dob,
      idType: passenger.idType,
      idNumber: passenger.idNumber,
    });
    this.formOpen.set(true);
  }

  closeForm(): void {
    this.formOpen.set(false);
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    const request = this.form.getRawValue();
    const editingId = this.editingId();
    this.submitting.set(true);

    const save$ = editingId
      ? this.passengerService.updatePassenger(editingId, request)
      : this.passengerService.createPassenger(request);

    save$
      .pipe(finalize(() => this.submitting.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (saved) => {
          this.passengers.update((current) =>
            editingId ? current.map((p) => (p.id === saved.id ? saved : p)) : [...current, saved],
          );
          this.notifications.success(editingId ? 'Passenger updated.' : 'Passenger added.');
          this.formOpen.set(false);
        },
      });
  }

  confirmDelete(id: number): void {
    this.confirmingDeleteId.set(id);
  }

  cancelDelete(): void {
    this.confirmingDeleteId.set(null);
  }

  deletePassenger(id: number): void {
    this.deletingId.set(id);
    this.passengerService
      .deletePassenger(id)
      .pipe(finalize(() => this.deletingId.set(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.passengers.update((current) => current.filter((p) => p.id !== id));
          this.confirmingDeleteId.set(null);
          this.notifications.success('Passenger removed.');
        },
      });
  }
}

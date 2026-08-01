import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { NotificationService } from '../../../core/services/notification.service';
import { PreferencesService } from '../../../core/services/preferences.service';

@Component({
  selector: 'tw-preferences',
  imports: [ReactiveFormsModule],
  templateUrl: './preferences.component.html',
  styleUrl: './preferences.component.scss',
})
export class PreferencesComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly preferencesService = inject(PreferencesService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly currencies = ['USD', 'EUR', 'GBP', 'JPY', 'AUD', 'CAD'];
  readonly seatPreferences = [
    { value: '', label: 'No preference' },
    { value: 'WINDOW', label: 'Window' },
    { value: 'AISLE', label: 'Aisle' },
  ];

  readonly loading = signal(true);
  readonly saving = signal(false);

  readonly form = this.fb.nonNullable.group({
    preferredCurrency: ['USD', Validators.required],
    seatPreference: [''],
    notificationsEnabled: [true],
  });

  ngOnInit(): void {
    this.preferencesService
      .getPreferences()
      .pipe(finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (preferences) => {
          this.form.setValue({
            preferredCurrency: preferences.preferredCurrency,
            seatPreference: preferences.seatPreference ?? '',
            notificationsEnabled: preferences.notificationsEnabled,
          });
        },
      });
  }

  save(): void {
    if (this.form.invalid || this.saving()) {
      return;
    }

    const value = this.form.getRawValue();
    this.saving.set(true);
    this.preferencesService
      .updatePreferences({
        preferredCurrency: value.preferredCurrency,
        seatPreference: value.seatPreference || null,
        notificationsEnabled: value.notificationsEnabled,
      })
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.notifications.success('Preferences saved.'),
      });
  }
}

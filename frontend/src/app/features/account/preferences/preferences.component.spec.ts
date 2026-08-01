import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { UserPreferencesResponse } from '../../../core/models/preferences.model';
import { NotificationService } from '../../../core/services/notification.service';
import { PreferencesService } from '../../../core/services/preferences.service';
import { PreferencesComponent } from './preferences.component';

describe('PreferencesComponent', () => {
  let fixture: ComponentFixture<PreferencesComponent>;
  let component: PreferencesComponent;
  let preferencesService: PreferencesService;
  let notifications: NotificationService;

  const preferences: UserPreferencesResponse = {
    userId: 1,
    preferredCurrency: 'USD',
    seatPreference: null,
    notificationsEnabled: true,
    updatedAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PreferencesComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(PreferencesComponent);
    component = fixture.componentInstance;
    preferencesService = TestBed.inject(PreferencesService);
    notifications = TestBed.inject(NotificationService);
  });

  it('loads and populates the form with the caller preferences', () => {
    vi.spyOn(preferencesService, 'getPreferences').mockReturnValue(of(preferences));
    fixture.detectChanges();

    expect(component.loading()).toBe(false);
    expect(component.form.value.preferredCurrency).toBe('USD');
    expect(component.form.value.seatPreference).toBe('');
  });

  it('saves the form and converts an empty seat preference to null', () => {
    vi.spyOn(preferencesService, 'getPreferences').mockReturnValue(of(preferences));
    const updateSpy = vi.spyOn(preferencesService, 'updatePreferences').mockReturnValue(
      of({ ...preferences, preferredCurrency: 'EUR' }),
    );
    const toastSpy = vi.spyOn(notifications, 'success');
    fixture.detectChanges();

    component.form.patchValue({ preferredCurrency: 'EUR', seatPreference: '' });
    component.save();

    expect(updateSpy).toHaveBeenCalledWith({
      preferredCurrency: 'EUR',
      seatPreference: null,
      notificationsEnabled: true,
    });
    expect(toastSpy).toHaveBeenCalledWith('Preferences saved.');
  });

  it('sends the selected seat preference when one is chosen', () => {
    vi.spyOn(preferencesService, 'getPreferences').mockReturnValue(of(preferences));
    const updateSpy = vi.spyOn(preferencesService, 'updatePreferences').mockReturnValue(of(preferences));
    fixture.detectChanges();

    component.form.patchValue({ seatPreference: 'WINDOW' });
    component.save();

    expect(updateSpy).toHaveBeenCalledWith(expect.objectContaining({ seatPreference: 'WINDOW' }));
  });
});

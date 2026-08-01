import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { PassengerService } from '../../core/services/passenger.service';
import { PreferencesService } from '../../core/services/preferences.service';
import { UserService } from '../../core/services/user.service';
import { AccountComponent } from './account.component';

describe('AccountComponent', () => {
  let fixture: ComponentFixture<AccountComponent>;
  let component: AccountComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccountComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountComponent);
    component = fixture.componentInstance;

    vi.spyOn(TestBed.inject(UserService), 'getCurrentUser').mockReturnValue(of());
    vi.spyOn(TestBed.inject(PassengerService), 'listMyPassengers').mockReturnValue(of([]));
    vi.spyOn(TestBed.inject(PreferencesService), 'getPreferences').mockReturnValue(of());
  });

  it('defaults to the Account Info tab', () => {
    fixture.detectChanges();

    expect(component.activeTab()).toBe('info');
  });

  it('switches tabs on selectTab', () => {
    fixture.detectChanges();

    component.selectTab('passengers');
    expect(component.activeTab()).toBe('passengers');

    component.selectTab('preferences');
    expect(component.activeTab()).toBe('preferences');
  });

  it('renders the child component matching the active tab', () => {
    fixture.detectChanges();
    let html = (fixture.nativeElement as HTMLElement).innerHTML;
    expect(html).toContain('tw-account-info');

    component.selectTab('passengers');
    fixture.detectChanges();
    html = (fixture.nativeElement as HTMLElement).innerHTML;
    expect(html).toContain('tw-saved-passengers');
  });
});

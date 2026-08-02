import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { UserResponse } from '../../../core/models/admin-user.model';
import { NotificationService } from '../../../core/services/notification.service';
import { UserService } from '../../../core/services/user.service';
import { AccountInfoComponent } from './account-info.component';

describe('AccountInfoComponent', () => {
  let fixture: ComponentFixture<AccountInfoComponent>;
  let component: AccountInfoComponent;
  let userService: UserService;
  let notifications: NotificationService;

  const user: UserResponse = {
    id: 1,
    username: 'alice',
    email: 'alice@example.com',
    role: 'CUSTOMER',
    partnerId: null,
    createdAt: '2026-01-01T00:00:00Z',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AccountInfoComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountInfoComponent);
    component = fixture.componentInstance;
    userService = TestBed.inject(UserService);
    notifications = TestBed.inject(NotificationService);
  });

  it('renders account details on success', () => {
    vi.spyOn(userService, 'getCurrentUser').mockReturnValue(of(user));
    fixture.detectChanges();

    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('alice');
    expect(html).toContain('alice@example.com');
    expect(html).toContain('CUSTOMER');
  });

  it('stops loading and renders the empty state on error', () => {
    vi.spyOn(userService, 'getCurrentUser').mockReturnValue(throwError(() => new Error('boom')));
    fixture.detectChanges();

    expect(component.loading()).toBe(false);
    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain("couldn't load your account");
  });

  it('startEditEmail pre-fills the form with the current email', () => {
    vi.spyOn(userService, 'getCurrentUser').mockReturnValue(of(user));
    fixture.detectChanges();

    component.startEditEmail();

    expect(component.editingEmail()).toBe(true);
    expect(component.emailForm.value.email).toBe('alice@example.com');
  });

  it('saveEmail updates the displayed user and shows a success toast', () => {
    vi.spyOn(userService, 'getCurrentUser').mockReturnValue(of(user));
    const updated = { ...user, email: 'new@example.com' };
    const updateSpy = vi.spyOn(userService, 'updateEmail').mockReturnValue(of(updated));
    const toastSpy = vi.spyOn(notifications, 'success');
    fixture.detectChanges();

    component.startEditEmail();
    component.emailForm.setValue({ email: 'new@example.com' });
    component.saveEmail();

    expect(updateSpy).toHaveBeenCalledWith({ email: 'new@example.com' });
    expect(component.user()?.email).toBe('new@example.com');
    expect(component.editingEmail()).toBe(false);
    expect(toastSpy).toHaveBeenCalledWith('Email updated.');
  });

  it('does not submit an invalid email', () => {
    vi.spyOn(userService, 'getCurrentUser').mockReturnValue(of(user));
    const updateSpy = vi.spyOn(userService, 'updateEmail');
    fixture.detectChanges();

    component.startEditEmail();
    component.emailForm.setValue({ email: 'not-an-email' });
    component.saveEmail();

    expect(updateSpy).not.toHaveBeenCalled();
  });

  it('savePassword calls the service and closes the form on success', () => {
    vi.spyOn(userService, 'getCurrentUser').mockReturnValue(of(user));
    const changeSpy = vi.spyOn(userService, 'changePassword').mockReturnValue(of(undefined));
    const toastSpy = vi.spyOn(notifications, 'success');
    fixture.detectChanges();

    component.startChangePassword();
    component.passwordForm.setValue({
      currentPassword: 'oldpass',
      newPassword: 'newpassword123',
      confirmPassword: 'newpassword123',
    });
    component.savePassword();

    expect(changeSpy).toHaveBeenCalledWith({ currentPassword: 'oldpass', newPassword: 'newpassword123' });
    expect(component.changingPassword()).toBe(false);
    expect(toastSpy).toHaveBeenCalledWith('Password changed.');
  });

  it('flags a mismatched confirmation and does not submit', () => {
    vi.spyOn(userService, 'getCurrentUser').mockReturnValue(of(user));
    const changeSpy = vi.spyOn(userService, 'changePassword');
    fixture.detectChanges();

    component.startChangePassword();
    component.passwordForm.setValue({
      currentPassword: 'oldpass',
      newPassword: 'newpassword123',
      confirmPassword: 'somethingelse',
    });
    component.savePassword();

    expect(component.passwordForm.hasError('passwordMismatch')).toBe(true);
    expect(changeSpy).not.toHaveBeenCalled();
  });
});

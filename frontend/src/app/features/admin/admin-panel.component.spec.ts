import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AuditLogResponse } from '../../core/models/audit.model';
import { UserResponse } from '../../core/models/admin-user.model';
import { AdminUserService } from '../../core/services/admin-user.service';
import { AuditService } from '../../core/services/audit.service';
import { NotificationService } from '../../core/services/notification.service';
import { AdminPanelComponent } from './admin-panel.component';

describe('AdminPanelComponent', () => {
  let fixture: ComponentFixture<AdminPanelComponent>;
  let component: AdminPanelComponent;
  let userService: AdminUserService;
  let auditService: AuditService;
  let notifications: NotificationService;

  const existingUser: UserResponse = {
    id: 1,
    username: 'alice',
    email: 'alice@example.com',
    role: 'CUSTOMER',
    createdAt: '2026-01-01T00:00:00Z',
  };

  async function createComponent() {
    await TestBed.configureTestingModule({
      imports: [AdminPanelComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    userService = TestBed.inject(AdminUserService);
    vi.spyOn(userService, 'listUsers').mockReturnValue(of([existingUser]));

    auditService = TestBed.inject(AuditService);
    notifications = TestBed.inject(NotificationService);

    fixture = TestBed.createComponent(AdminPanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => await createComponent());

  it('loads the user list on init, defaulting to the users tab', () => {
    expect(component.tab()).toBe('users');
    expect(component.loadingUsers()).toBe(false);
    expect(component.users()).toEqual([existingUser]);
  });

  it('selectTab("audit") lazily loads the audit log only the first time', () => {
    const entries: AuditLogResponse[] = [
      { id: 1, actorUsername: 'alice', action: 'USER_CREATED', entityType: 'User', entityId: 1, details: null, createdAt: '2026-01-01T00:00:00Z' },
    ];
    vi.spyOn(auditService, 'listAudit').mockReturnValue(of(entries));

    component.selectTab('audit');
    expect(component.tab()).toBe('audit');
    expect(auditService.listAudit).toHaveBeenCalledTimes(1);
    expect(component.auditLog()).toEqual(entries);

    component.selectTab('users');
    component.selectTab('audit');
    expect(auditService.listAudit).toHaveBeenCalledTimes(1);
  });

  it('createUser does nothing while the form is invalid', () => {
    const createSpy = vi.spyOn(userService, 'createUser');
    component.newUserForm.patchValue({ email: 'not-an-email' });

    component.createUser();

    expect(createSpy).not.toHaveBeenCalled();
  });

  it('createUser submits the form, appends the new user, and resets the form', () => {
    const created: UserResponse = { id: 2, username: 'newop', email: 'newop@example.com', role: 'OPERATOR', createdAt: '2026-01-02T00:00:00Z' };
    vi.spyOn(userService, 'createUser').mockReturnValue(of(created));
    const successSpy = vi.spyOn(notifications, 'success');
    component.newUserForm.setValue({
      username: 'newop',
      email: 'newop@example.com',
      password: 'password123',
      role: 'OPERATOR',
    });

    component.createUser();

    expect(userService.createUser).toHaveBeenCalledWith({
      username: 'newop',
      email: 'newop@example.com',
      password: 'password123',
      role: 'OPERATOR',
    });
    expect(component.users()).toEqual([existingUser, created]);
    expect(successSpy).toHaveBeenCalledWith("Account 'newop' created as OPERATOR.");
    expect(component.newUserForm.getRawValue().username).toBe('');
  });

  it('changeRole does nothing when the selected role matches the current role', () => {
    const updateSpy = vi.spyOn(userService, 'updateRole');
    const event = { target: { value: 'CUSTOMER' } } as unknown as Event;

    component.changeRole(existingUser, event);

    expect(updateSpy).not.toHaveBeenCalled();
  });

  it('changeRole submits the new role and updates the user in place', () => {
    const updated: UserResponse = { ...existingUser, role: 'SUPPORT' };
    vi.spyOn(userService, 'updateRole').mockReturnValue(of(updated));
    const successSpy = vi.spyOn(notifications, 'success');
    const event = { target: { value: 'SUPPORT' } } as unknown as Event;

    component.changeRole(existingUser, event);

    expect(userService.updateRole).toHaveBeenCalledWith(1, { role: 'SUPPORT' });
    expect(component.users()).toEqual([updated]);
    expect(successSpy).toHaveBeenCalledWith('alice is now SUPPORT.');
  });
});

import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { AuditLogResponse } from '../../core/models/audit.model';
import { UserRole, UserResponse } from '../../core/models/admin-user.model';
import { AdminUserService } from '../../core/services/admin-user.service';
import { AuditService } from '../../core/services/audit.service';
import { NotificationService } from '../../core/services/notification.service';

type AdminTab = 'users' | 'audit';

@Component({
  selector: 'tw-admin-panel',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './admin-panel.component.html',
  styleUrl: './admin-panel.component.scss',
})
export class AdminPanelComponent {
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(AdminUserService);
  private readonly auditService = inject(AuditService);
  private readonly notifications = inject(NotificationService);

  readonly tab = signal<AdminTab>('users');
  readonly users = signal<UserResponse[]>([]);
  readonly loadingUsers = signal(true);
  readonly creatingUser = signal(false);
  readonly updatingRoleForUserId = signal<number | null>(null);
  readonly auditLog = signal<AuditLogResponse[]>([]);
  readonly loadingAudit = signal(false);

  readonly newUserForm = this.fb.nonNullable.group({
    username: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    role: this.fb.nonNullable.control<UserRole>('OPERATOR'),
  });

  constructor() {
    this.refreshUsers();
  }

  selectTab(tab: AdminTab): void {
    this.tab.set(tab);
    if (tab === 'audit' && this.auditLog().length === 0) {
      this.refreshAudit();
    }
  }

  private refreshUsers(): void {
    this.loadingUsers.set(true);
    this.userService
      .listUsers()
      .pipe(finalize(() => this.loadingUsers.set(false)))
      .subscribe({ next: (users) => this.users.set(users) });
  }

  private refreshAudit(): void {
    this.loadingAudit.set(true);
    this.auditService
      .listAudit()
      .pipe(finalize(() => this.loadingAudit.set(false)))
      .subscribe({ next: (entries) => this.auditLog.set(entries) });
  }

  createUser(): void {
    if (this.newUserForm.invalid || this.creatingUser()) {
      return;
    }

    this.creatingUser.set(true);
    this.userService
      .createUser(this.newUserForm.getRawValue())
      .pipe(finalize(() => this.creatingUser.set(false)))
      .subscribe({
        next: (user) => {
          this.users.update((current) => [...current, user]);
          this.notifications.success(`Account '${user.username}' created as ${user.role}.`);
          this.newUserForm.reset({ username: '', email: '', password: '', role: 'OPERATOR' });
        },
      });
  }

  changeRole(user: UserResponse, event: Event): void {
    const role = (event.target as HTMLSelectElement).value as UserRole;
    if (role === user.role) {
      return;
    }

    this.updatingRoleForUserId.set(user.id);
    this.userService
      .updateRole(user.id, { role })
      .pipe(finalize(() => this.updatingRoleForUserId.set(null)))
      .subscribe({
        next: (updated) => {
          this.users.update((current) => current.map((u) => (u.id === updated.id ? updated : u)));
          this.notifications.success(`${updated.username} is now ${updated.role}.`);
        },
      });
  }
}

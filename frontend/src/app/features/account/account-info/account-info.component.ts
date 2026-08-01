import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { UserResponse } from '../../../core/models/admin-user.model';
import { NotificationService } from '../../../core/services/notification.service';
import { UserService } from '../../../core/services/user.service';

function passwordsMatchValidator(control: AbstractControl): ValidationErrors | null {
  const newPassword = control.get('newPassword')?.value;
  const confirmPassword = control.get('confirmPassword')?.value;
  return newPassword && confirmPassword && newPassword !== confirmPassword ? { passwordMismatch: true } : null;
}

@Component({
  selector: 'tw-account-info',
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: './account-info.component.html',
  styleUrl: './account-info.component.scss',
})
export class AccountInfoComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(true);
  readonly user = signal<UserResponse | null>(null);

  readonly editingEmail = signal(false);
  readonly savingEmail = signal(false);

  readonly changingPassword = signal(false);
  readonly savingPassword = signal(false);

  readonly emailForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  readonly passwordForm = this.fb.nonNullable.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatchValidator },
  );

  ngOnInit(): void {
    this.userService
      .getCurrentUser()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (user) => {
          this.user.set(user);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  initial(username: string): string {
    return username.charAt(0).toUpperCase();
  }

  startEditEmail(): void {
    const current = this.user();
    this.emailForm.reset({ email: current?.email ?? '' });
    this.editingEmail.set(true);
  }

  cancelEditEmail(): void {
    this.editingEmail.set(false);
  }

  saveEmail(): void {
    if (this.emailForm.invalid || this.savingEmail()) {
      return;
    }

    this.savingEmail.set(true);
    this.userService
      .updateEmail(this.emailForm.getRawValue())
      .pipe(finalize(() => this.savingEmail.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.user.set(updated);
          this.editingEmail.set(false);
          this.notifications.success('Email updated.');
        },
      });
  }

  startChangePassword(): void {
    this.passwordForm.reset({ currentPassword: '', newPassword: '', confirmPassword: '' });
    this.changingPassword.set(true);
  }

  cancelChangePassword(): void {
    this.changingPassword.set(false);
  }

  savePassword(): void {
    if (this.passwordForm.invalid || this.savingPassword()) {
      return;
    }

    const { currentPassword, newPassword } = this.passwordForm.getRawValue();
    this.savingPassword.set(true);
    this.userService
      .changePassword({ currentPassword, newPassword })
      .pipe(finalize(() => this.savingPassword.set(false)), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.changingPassword.set(false);
          this.notifications.success('Password changed.');
        },
      });
  }
}

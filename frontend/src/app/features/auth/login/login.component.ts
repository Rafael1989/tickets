import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'tw-login',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly submitting = signal(false);

  /** Preserved onto the Register link too, so signing up mid-checkout still returns you to checkout. */
  readonly redirectTo = this.route.snapshot.queryParamMap.get('redirectTo');

  /**
   * Explains why an unauthenticated visitor was bounced here, instead of dropping them on a bare
   * form with no context. Checkout gets its own wording because a guest's seat picks live only in
   * the browser (see SeatSelectionComponent's guestSelectedSeatIds) — nothing is actually reserved
   * server-side until they sign in, so promising "your seats are held" here would be a lie.
   */
  readonly redirectMessage = this.redirectTo
    ? this.redirectTo.startsWith('/checkout')
      ? 'Sign in to finish your booking — we kept your seat selection, but the seats stay up for grabs until you check out.'
      : 'Please sign in to continue.'
    : null;

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.auth
      .login(this.form.getRawValue())
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => {
          const redirectTo = this.route.snapshot.queryParamMap.get('redirectTo');
          // Falls back to the role's own landing screen, not a hardcoded '/search' — an operator,
          // support agent or admin has no customer search page to land on.
          this.router.navigateByUrl(redirectTo ?? this.auth.homePath());
        },
      });
  }
}

import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'tw-navbar',
  imports: [RouterLink],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.scss',
})
export class NavbarComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly isAuthenticated = this.auth.isAuthenticated;
  readonly username = this.auth.username;
  readonly homePath = this.auth.homePath;
  readonly isCustomer = this.auth.isCustomer;
  readonly isOperator = computed(() => this.auth.hasRole('OPERATOR'));
  readonly isSupport = computed(() => this.auth.hasRole('SUPPORT'));
  readonly isAdmin = computed(() => this.auth.hasRole('ADMIN'));

  logout(): void {
    this.auth.logout();
    // Reads homePath *after* logout, so it resolves to the public '/search' rather than the
    // signed-out user's old portal.
    this.router.navigate([this.auth.homePath()]);
  }
}

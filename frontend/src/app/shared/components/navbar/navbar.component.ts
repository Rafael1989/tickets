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
  readonly isOperator = computed(() => this.auth.hasRole('OPERATOR'));
  readonly isSupport = computed(() => this.auth.hasRole('SUPPORT'));
  readonly isAdmin = computed(() => this.auth.hasRole('ADMIN'));

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/search']);
  }
}

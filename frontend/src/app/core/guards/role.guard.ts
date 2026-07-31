import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Route-data-driven so one guard factory covers every role-gated section
 * (operator/support/admin) instead of writing a near-identical guard per
 * role. Usage: { canActivate: [roleGuard], data: { role: 'OPERATOR' } }.
 */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const requiredRole = route.data['role'] as string;

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }

  if (auth.hasRole(requiredRole)) {
    return true;
  }

  return router.createUrlTree(['/search']);
};

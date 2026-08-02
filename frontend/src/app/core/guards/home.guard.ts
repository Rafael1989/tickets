import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Sends "/" (and any unmatched URL) to the landing screen for whoever is signed in, instead of a
 * fixed redirect to the customer search page — staff roles don't have that screen in their
 * navigation at all. Always returns a UrlTree, so it redirects rather than ever activating.
 */
export const homeGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return router.parseUrl(auth.homePath());
};

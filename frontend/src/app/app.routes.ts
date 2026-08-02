import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { homeGuard } from './core/guards/home.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', canActivate: [homeGuard], children: [] },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'search',
    loadComponent: () => import('./features/search/search.component').then((m) => m.SearchComponent),
  },
  {
    path: 'schedules/:id',
    loadComponent: () =>
      import('./features/schedule/seat-selection.component').then((m) => m.SeatSelectionComponent),
  },
  {
    path: 'find-booking',
    loadComponent: () =>
      import('./features/booking/guest-lookup/guest-lookup.component').then((m) => m.GuestLookupComponent),
  },
  {
    path: 'checkout',
    canActivate: [authGuard],
    loadComponent: () => import('./features/checkout/checkout.component').then((m) => m.CheckoutComponent),
  },
  {
    path: 'account',
    canActivate: [authGuard],
    loadComponent: () => import('./features/account/account.component').then((m) => m.AccountComponent),
  },
  {
    path: 'bookings',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/booking/my-bookings/my-bookings.component').then((m) => m.MyBookingsComponent),
  },
  {
    path: 'bookings/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/booking/booking-details.component').then((m) => m.BookingDetailsComponent),
  },
  {
    path: 'operator',
    canActivate: [roleGuard],
    data: { role: 'OPERATOR' },
    loadComponent: () =>
      import('./features/operator/operator-portal.component').then((m) => m.OperatorPortalComponent),
  },
  {
    path: 'support',
    canActivate: [roleGuard],
    data: { role: 'SUPPORT' },
    loadComponent: () =>
      import('./features/support/support-panel.component').then((m) => m.SupportPanelComponent),
  },
  {
    path: 'admin',
    canActivate: [roleGuard],
    data: { role: 'ADMIN' },
    loadComponent: () => import('./features/admin/admin-panel.component').then((m) => m.AdminPanelComponent),
  },
  { path: '**', canActivate: [homeGuard], children: [] },
];

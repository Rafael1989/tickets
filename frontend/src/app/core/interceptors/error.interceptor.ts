import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { ErrorResponse } from '../models/error.model';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notifications = inject(NotificationService);
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        if (error.status === 401) {
          auth.logout();
          router.navigate(['/login']);
          notifications.error('Your session has expired. Please sign in again.');
        } else if (error.status >= 400) {
          const body = error.error as Partial<ErrorResponse> | null;
          notifications.error(body?.message ?? 'Something went wrong. Please try again.');
        }
      }
      return throwError(() => error);
    }),
  );
};

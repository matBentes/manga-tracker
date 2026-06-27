import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

/**
 * Attaches the auth cookie to every request (`withCredentials`) and, when a protected call comes
 * back 401, sends the user to `/login`. Auth endpoints themselves are excluded from the redirect so
 * a failed login shows an inline error instead of bouncing (and to avoid a redirect loop on `/me`).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const authReq = req.clone({ withCredentials: true });
  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !req.url.includes('/api/auth/')) {
        router.navigate(['/login']);
      }
      return throwError(() => err);
    }),
  );
};

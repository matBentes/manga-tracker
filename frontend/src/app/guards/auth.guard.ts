import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Lets visitors in without a login wall: the demo list is the public landing experience. First we
 * ask the server whether there is an authenticated session (`GET /api/auth/me`). If there is, the
 * route is allowed (owner or returning demo visitor). If not, we transparently establish a demo
 * session (`POST /api/auth/demo-login`) and allow the route — so recruiters see demo data with no
 * sign-in. Only if the demo login also fails do we send the user to `/login` (owner's entry point).
 * The cookie is httpOnly and invisible to JS, so the server is the source of truth in both calls.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.me().pipe(
    map(() => true),
    catchError(() =>
      auth.demoLogin().pipe(
        map(() => true),
        catchError(() => of(router.createUrlTree(['/login']))),
      ),
    ),
  );
};

import {
  HttpBackend,
  HttpClient,
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import {
  catchError,
  finalize,
  map,
  Observable,
  of,
  shareReplay,
  switchMap,
  tap,
  throwError,
} from 'rxjs';
import { environment } from '../../environments/environment';

interface CsrfResponse {
  token: string;
}

const CSRF_HEADER = 'X-XSRF-TOKEN';
const CSRF_ENDPOINT = `${environment.apiUrl}/api/auth/csrf`;
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

let csrfToken: string | null = null;
let csrfTokenRequest$: Observable<string> | null = null;

/**
 * Attaches the auth cookie to every request (`withCredentials`) and, when a protected call comes
 * back 401, sends the user to `/login`. Auth endpoints themselves are excluded from the redirect so
 * a failed login shows an inline error instead of bouncing (and to avoid a redirect loop on `/me`).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const httpBackend = inject(HttpBackend);
  const authReq = req.clone({ withCredentials: true });

  if (requiresCsrf(req)) {
    return loadCsrfToken(httpBackend).pipe(
      switchMap((token) => next(authReq.clone({ setHeaders: { [CSRF_HEADER]: token } }))),
      catchError((err: HttpErrorResponse) => handleAuthError(err, router, req.url)),
    );
  }

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      return handleAuthError(err, router, req.url);
    }),
  );
};

function requiresCsrf(req: HttpRequest<unknown>): boolean {
  return isApiRequest(req.url) && !SAFE_METHODS.has(req.method.toUpperCase());
}

function isApiRequest(url: string): boolean {
  const apiPrefix = `${environment.apiUrl}/api/`;
  return url.startsWith(apiPrefix);
}

function loadCsrfToken(httpBackend: HttpBackend): Observable<string> {
  if (csrfToken) {
    return of(csrfToken);
  }

  if (!csrfTokenRequest$) {
    const http = new HttpClient(httpBackend);
    csrfTokenRequest$ = http.get<CsrfResponse>(CSRF_ENDPOINT, { withCredentials: true }).pipe(
      map((response) => response.token),
      tap((token) => {
        csrfToken = token;
      }),
      finalize(() => {
        csrfTokenRequest$ = null;
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
  }

  return csrfTokenRequest$;
}

function handleAuthError(err: HttpErrorResponse, router: Router, requestUrl: string) {
  if (err.status === 403) {
    csrfToken = null;
  }
  if (err.status === 401 && !requestUrl.includes('/api/auth/')) {
    router.navigate(['/login']);
  }
  return throwError(() => err);
}

import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AuthUser {
  username: string;
  role: string;
}

/**
 * Cookie-based JWT auth. The backend sets an httpOnly cookie on login, so the token is never
 * readable from JS; every request must ride with `withCredentials: true` for the cookie to attach.
 * `user` mirrors the last known auth state for the UI; the cookie remains the source of truth.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/auth`;

  private readonly currentUser = signal<AuthUser | null>(null);
  readonly user = this.currentUser.asReadonly();

  login(username: string, password: string): Observable<AuthUser> {
    return this.http
      .post<AuthUser>(`${this.baseUrl}/login`, { username, password }, { withCredentials: true })
      .pipe(tap((u) => this.currentUser.set(u)));
  }

  /**
   * Passwordless entry to the shared demo account (the public landing experience). The backend
   * issues the same httpOnly cookie as a normal login but for the seeded `demo` user, so the demo
   * password is never sent to the browser.
   */
  demoLogin(): Observable<AuthUser> {
    return this.http
      .post<AuthUser>(`${this.baseUrl}/demo-login`, {}, { withCredentials: true })
      .pipe(tap((u) => this.currentUser.set(u)));
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(`${this.baseUrl}/logout`, {}, { withCredentials: true })
      .pipe(tap(() => this.currentUser.set(null)));
  }

  me(): Observable<AuthUser> {
    return this.http
      .get<AuthUser>(`${this.baseUrl}/me`, { withCredentials: true })
      .pipe(tap((u) => this.currentUser.set(u)));
  }
}

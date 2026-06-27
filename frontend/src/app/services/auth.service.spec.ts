import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [AuthService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('login posts credentials with credentials and stores the user', () => {
    let result: unknown;
    service.login('owner', 'pw').subscribe((u) => (result = u));

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
    expect(req.request.body).toEqual({ username: 'owner', password: 'pw' });
    req.flush({ username: 'owner', role: 'OWNER' });

    expect(result).toEqual({ username: 'owner', role: 'OWNER' });
    expect(service.user()).toEqual({ username: 'owner', role: 'OWNER' });
  });

  it('logout clears the stored user', () => {
    service.login('owner', 'pw').subscribe();
    httpMock.expectOne('/api/auth/login').flush({ username: 'owner', role: 'OWNER' });

    service.logout().subscribe();
    const req = httpMock.expectOne('/api/auth/logout');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
    req.flush(null);

    expect(service.user()).toBeNull();
  });

  it('demoLogin posts with credentials and stores the demo user', () => {
    let result: unknown;
    service.demoLogin().subscribe((u) => (result = u));

    const req = httpMock.expectOne('/api/auth/demo-login');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
    req.flush({ username: 'demo', role: 'DEMO' });

    expect(result).toEqual({ username: 'demo', role: 'DEMO' });
    expect(service.user()).toEqual({ username: 'demo', role: 'DEMO' });
  });

  it('me fetches the current user with credentials', () => {
    service.me().subscribe();
    const req = httpMock.expectOne('/api/auth/me');
    expect(req.request.method).toBe('GET');
    expect(req.request.withCredentials).toBe(true);
    req.flush({ username: 'demo', role: 'DEMO' });

    expect(service.user()).toEqual({ username: 'demo', role: 'DEMO' });
  });
});

import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigate: vi.fn() } },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('fetches csrf token and attaches it to unsafe api requests', () => {
    http.post('/api/manga', { sourceUrl: 'https://example.test/manga' }).subscribe();

    const csrfReq = httpMock.expectOne('/api/auth/csrf');
    expect(csrfReq.request.method).toBe('GET');
    expect(csrfReq.request.withCredentials).toBe(true);
    csrfReq.flush({ token: 'csrf-token' });

    const request = httpMock.expectOne('/api/manga');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
    request.flush({});
  });

  it('does not fetch csrf token for safe api requests', () => {
    http.get('/api/auth/me').subscribe();

    const request = httpMock.expectOne('/api/auth/me');
    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    request.flush({ username: 'demo', role: 'DEMO' });
  });
});

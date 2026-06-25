import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { SwPush } from '@angular/service-worker';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PushService } from './push.service';

/** Resolve all pending microtasks (chained awaits) before asserting on mock HTTP requests. */
const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

function createSwPushMock(overrides: Partial<Record<keyof SwPush, unknown>> = {}) {
  return {
    isEnabled: true,
    subscription: of(null),
    requestSubscription: vi.fn(),
    unsubscribe: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  } as unknown as SwPush;
}

function setup(swPush: SwPush) {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      PushService,
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: SwPush, useValue: swPush },
    ],
  });
  return {
    service: TestBed.inject(PushService),
    httpMock: TestBed.inject(HttpTestingController),
  };
}

describe('PushService', () => {
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
  });

  it('isSupported reflects swPush.isEnabled', () => {
    const { service } = setup(createSwPushMock({ isEnabled: false }));
    expect(service.isSupported).toBe(false);
  });

  it('enableNotifications fetches the public key, subscribes, and posts the subscription', async () => {
    const fakeSub = {
      endpoint: 'https://push/a',
      toJSON: () => ({ endpoint: 'https://push/a', keys: { p256dh: 'k', auth: 's' } }),
    } as unknown as PushSubscription;
    const swPush = createSwPushMock({
      requestSubscription: vi.fn().mockResolvedValue(fakeSub),
    });
    const { service, httpMock } = setup(swPush);

    const promise = service.enableNotifications();

    await flush();
    httpMock.expectOne('/api/push/public-key').flush({ publicKey: 'BPublicKey' });
    await flush();
    const subscribeReq = httpMock.expectOne('/api/push/subscribe');
    expect(subscribeReq.request.body).toEqual({
      endpoint: 'https://push/a',
      keys: { p256dh: 'k', auth: 's' },
    });
    subscribeReq.flush(null);

    await promise;
    expect(swPush.requestSubscription).toHaveBeenCalledWith({ serverPublicKey: 'BPublicKey' });
  });

  it('enableNotifications throws when push is unsupported', async () => {
    const { service } = setup(createSwPushMock({ isEnabled: false }));
    await expect(service.enableNotifications()).rejects.toThrow(/not supported/);
  });

  it('disableNotifications unsubscribes and notifies the backend', async () => {
    const fakeSub = { endpoint: 'https://push/a' } as unknown as PushSubscription;
    const swPush = createSwPushMock({ subscription: of(fakeSub) });
    const { service, httpMock } = setup(swPush);

    const promise = service.disableNotifications();
    await flush();

    const req = httpMock.expectOne('/api/push/unsubscribe');
    expect(req.request.body).toEqual({ endpoint: 'https://push/a' });
    req.flush(null);

    await promise;
    expect(swPush.unsubscribe).toHaveBeenCalled();
  });

  it('disableNotifications is a no-op when no subscription exists', async () => {
    const swPush = createSwPushMock({ subscription: of(null) });
    const { service } = setup(swPush);
    await service.disableNotifications();
    expect(swPush.unsubscribe).not.toHaveBeenCalled();
  });
});

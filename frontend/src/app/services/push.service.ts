import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SwPush } from '@angular/service-worker';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

interface PublicKeyResponse {
  publicKey: string;
}

/**
 * Manages the browser Web Push subscription: requests notification permission, subscribes via the
 * service worker using the backend VAPID public key, and registers/removes the subscription on the
 * backend. Push delivery and notification taps are handled by the Angular service worker.
 */
@Injectable({
  providedIn: 'root',
})
export class PushService {
  private readonly swPush = inject(SwPush);
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/api/push`;

  /** Whether the service worker (and therefore push) is available in this context. */
  get isSupported(): boolean {
    return this.swPush.isEnabled;
  }

  /** True once the user has granted notification permission and an active subscription exists. */
  async isSubscribed(): Promise<boolean> {
    if (!this.swPush.isEnabled) {
      return false;
    }
    const subscription = await firstValueFrom(this.swPush.subscription);
    return subscription != null;
  }

  /** Request permission, subscribe, and persist the subscription on the backend. */
  async enableNotifications(): Promise<void> {
    if (!this.swPush.isEnabled) {
      throw new Error('Push notifications are not supported in this browser.');
    }
    const { publicKey } = await firstValueFrom(
      this.http.get<PublicKeyResponse>(`${this.apiUrl}/public-key`),
    );
    if (!publicKey) {
      throw new Error('Server is missing a VAPID public key.');
    }
    const subscription = await this.swPush.requestSubscription({ serverPublicKey: publicKey });
    await firstValueFrom(this.http.post<void>(`${this.apiUrl}/subscribe`, subscription.toJSON()));
  }

  /** Remove the current subscription locally and on the backend. */
  async disableNotifications(): Promise<void> {
    if (!this.swPush.isEnabled) {
      return;
    }
    const subscription = await firstValueFrom(this.swPush.subscription);
    if (!subscription) {
      return;
    }
    const endpoint = subscription.endpoint;
    await this.swPush.unsubscribe();
    await firstValueFrom(this.http.post<void>(`${this.apiUrl}/unsubscribe`, { endpoint }));
  }
}

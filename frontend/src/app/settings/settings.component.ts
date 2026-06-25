import { Component, OnInit, inject } from '@angular/core';

import { PushService } from '../services/push.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  private readonly pushService = inject(PushService);

  /** When the daily new-chapter check runs (informational only). */
  readonly dailyCheckLabel = 'Every day at 08:00 (America/São_Paulo)';

  pushSupported = false;
  pushEnabled = false;
  pushBusy = false;
  pushError: string | null = null;

  ngOnInit(): void {
    this.pushSupported = this.pushService.isSupported;
    if (!this.pushSupported) {
      return;
    }
    this.pushService
      .isSubscribed()
      .then((subscribed) => {
        this.pushEnabled = subscribed;
      })
      .catch(() => {
        this.pushEnabled = false;
      });
  }

  async onTogglePush(): Promise<void> {
    if (this.pushBusy) {
      return;
    }
    this.pushBusy = true;
    this.pushError = null;
    try {
      if (this.pushEnabled) {
        await this.pushService.disableNotifications();
        this.pushEnabled = false;
      } else {
        await this.pushService.enableNotifications();
        this.pushEnabled = true;
      }
    } catch (err) {
      this.pushError =
        err instanceof Error ? err.message : 'Could not change phone notification settings.';
    } finally {
      this.pushBusy = false;
    }
  }
}

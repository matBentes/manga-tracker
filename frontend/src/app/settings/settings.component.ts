import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { AppSettings, SettingsService, UpdateSettingsRequest } from '../services/settings.service';
import { PushService } from '../services/push.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  private readonly settingsService = inject(SettingsService);
  private readonly pushService = inject(PushService);

  isLoading = false;
  isSaving = false;
  loadError: string | null = null;
  saveError: string | null = null;
  saveSuccess = false;

  pushSupported = false;
  pushEnabled = false;
  pushBusy = false;
  pushError: string | null = null;

  pollIntervalMinutes = 30;

  ngOnInit(): void {
    this.loadSettings();
    this.initPushState();
  }

  private initPushState(): void {
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

  private loadSettings(): void {
    this.isLoading = true;
    this.loadError = null;
    this.settingsService.getSettings().subscribe({
      next: (settings: AppSettings) => {
        this.pollIntervalMinutes = settings.pollIntervalMinutes;
        this.isLoading = false;
      },
      error: () => {
        this.loadError = 'Failed to load settings. Please refresh the page.';
        this.isLoading = false;
      },
    });
  }

  onSave(): void {
    this.isSaving = true;
    this.saveError = null;
    this.saveSuccess = false;

    const request: UpdateSettingsRequest = {
      pollIntervalMinutes: this.pollIntervalMinutes,
    };

    this.settingsService.updateSettings(request).subscribe({
      next: () => {
        this.isSaving = false;
        this.saveSuccess = true;
        setTimeout(() => {
          this.saveSuccess = false;
        }, 3000);
      },
      error: (err: HttpErrorResponse) => {
        this.isSaving = false;
        if (err.status === 400) {
          this.saveError = 'Invalid settings. Please check the poll interval.';
        } else {
          this.saveError = 'Failed to save settings. Please try again.';
        }
      },
    });
  }
}

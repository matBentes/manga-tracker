import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { AppSettings, SettingsService, UpdateSettingsRequest } from '../services/settings.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  private readonly settingsService = inject(SettingsService);

  isLoading = false;
  isSaving = false;
  loadError: string | null = null;
  saveError: string | null = null;
  saveSuccess = false;

  emailNotificationsEnabled = false;
  notificationEmail = '';
  pollIntervalMinutes = 30;

  ngOnInit(): void {
    this.loadSettings();
  }

  private loadSettings(): void {
    this.isLoading = true;
    this.loadError = null;
    this.settingsService.getSettings().subscribe({
      next: (settings: AppSettings) => {
        this.emailNotificationsEnabled = settings.emailNotificationsEnabled;
        this.notificationEmail = settings.notificationEmail;
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
      emailNotificationsEnabled: this.emailNotificationsEnabled,
      notificationEmail: this.notificationEmail,
      pollIntervalMinutes: this.pollIntervalMinutes,
    };

    this.settingsService.updateSettings(request).subscribe({
      next: () => {
        this.isSaving = false;
        this.saveSuccess = true;
      },
      error: (err: HttpErrorResponse) => {
        this.isSaving = false;
        if (err.status === 400) {
          this.saveError = 'Invalid settings. Please check your email and poll interval.';
        } else {
          this.saveError = 'Failed to save settings. Please try again.';
        }
      },
    });
  }
}

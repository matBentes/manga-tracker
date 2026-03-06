import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface AppSettings {
  id: number;
  emailNotificationsEnabled: boolean;
  notificationEmail: string;
  pollIntervalMinutes: number;
}

export interface UpdateSettingsRequest {
  emailNotificationsEnabled?: boolean;
  notificationEmail?: string;
  pollIntervalMinutes?: number;
}

@Injectable({
  providedIn: 'root',
})
export class SettingsService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/api/settings`;

  getSettings(): Observable<AppSettings> {
    return this.http.get<AppSettings>(this.apiUrl);
  }

  updateSettings(settings: UpdateSettingsRequest): Observable<AppSettings> {
    return this.http.put<AppSettings>(this.apiUrl, settings);
  }
}

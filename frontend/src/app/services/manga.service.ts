import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Manga {
  id: string;
  title: string;
  sourceUrl: string;
  currentChapter: number;
  latestChapter: number;
  notificationsEnabled: boolean;
  lastCheckedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MangaPatch {
  currentChapter?: number;
  notificationsEnabled?: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class MangaService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/api/manga`;

  getManga(): Observable<Manga[]> {
    return this.http.get<Manga[]>(this.apiUrl);
  }

  addManga(sourceUrl: string): Observable<Manga> {
    return this.http.post<Manga>(this.apiUrl, { sourceUrl });
  }

  updateManga(id: string, patch: MangaPatch): Observable<Manga> {
    return this.http.patch<Manga>(`${this.apiUrl}/${id}`, patch);
  }

  deleteManga(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}

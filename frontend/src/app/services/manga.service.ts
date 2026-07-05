import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Manga {
  id: string;
  title: string;
  sourceUrl: string | null;
  mangadexId: string | null;
  currentChapter: number;
  latestChapter: number;
  coverImageUrl: string | null;
  readingStatus: string;
  latestChapterAt: string | null;
  notificationsEnabled: boolean;
  lastCheckedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MangaSearchResult {
  mangaDexId: string;
  title: string;
  description: string | null;
  coverImageUrl: string | null;
}

export interface AddMangaPayload {
  mangaDexId: string;
  sourceUrl?: string;
  currentChapter?: number;
  readingStatus?: string;
}

export interface MangaPatch {
  notificationsEnabled?: boolean;
  currentChapter?: number;
  latestChapter?: number;
  readingStatus?: string;
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

  searchManga(q: string): Observable<MangaSearchResult[]> {
    return this.http.get<MangaSearchResult[]>(`${this.apiUrl}/search`, { params: { q } });
  }

  addManga(payload: AddMangaPayload): Observable<Manga> {
    return this.http.post<Manga>(this.apiUrl, payload);
  }

  updateManga(id: string, patch: MangaPatch): Observable<Manga> {
    return this.http.patch<Manga>(`${this.apiUrl}/${id}`, patch);
  }

  /** Mark a manga as fully read (caught up to its latest chapter). */
  markRead(id: string): Observable<Manga> {
    return this.http.post<Manga>(`${this.apiUrl}/${id}/read`, {});
  }

  /** Mark a manga unread again (undo a mark-read). */
  markUnread(id: string): Observable<Manga> {
    return this.http.post<Manga>(`${this.apiUrl}/${id}/unread`, {});
  }

  deleteManga(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** Send a test/demo notification for the latest chapter of a manga. */
  testPush(id: string): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${id}/test-push`, {});
  }
}

import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, type Observable } from 'rxjs';

import { AddMangaFormComponent } from '../add-manga/add-manga-form.component';
import { Manga, MangaService } from '../services/manga.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [AddMangaFormComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly mangaService = inject(MangaService);
  private readonly destroyRef = inject(DestroyRef);

  readonly statusOptions = [
    { value: 'READING', label: 'Reading' },
    { value: 'COMPLETED', label: 'Completed' },
    { value: 'ON_HOLD', label: 'On hold' },
    { value: 'DROPPED', label: 'Dropped' },
    { value: 'PLAN_TO_READ', label: 'Plan to read' },
  ];

  mangaList: Manga[] = [];
  isLoading = false;
  error: string | null = null;
  actionError: Record<string, string | null> = {};
  busy: Record<string, boolean> = {};
  isMarkingAll = false;

  ngOnInit(): void {
    this.loadManga();
  }

  loadManga(): void {
    this.isLoading = true;
    this.error = null;
    this.mangaService
      .getManga()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (manga) => {
          this.mangaList = manga;
          this.isLoading = false;
        },
        error: () => {
          this.error = 'Failed to load manga list. Please try again.';
          this.isLoading = false;
        },
      });
  }

  isUnread(manga: Manga): boolean {
    return manga.latestChapter > manga.currentChapter;
  }

  get unreadCount(): number {
    return this.mangaList.filter((m) => this.isUnread(m)).length;
  }

  /** Toggle a manga between read (caught up) and unread — the latter undoes a mark-read. */
  toggleRead(manga: Manga): void {
    if (this.busy[manga.id]) {
      return;
    }
    const wasUnread = this.isUnread(manga);
    const request$ = wasUnread
      ? this.mangaService.markRead(manga.id)
      : this.mangaService.markUnread(manga.id);

    this.runAction(
      manga.id,
      request$,
      (updated) => {
        this.applyMangaUpdate(manga, updated);
      },
      () => {
        this.actionError[manga.id] = wasUnread ? 'Failed to mark as read.' : 'Failed to undo.';
      },
    );
  }

  markAllRead(): void {
    const unread = this.mangaList.filter((m) => this.isUnread(m));
    if (this.isMarkingAll || unread.length === 0) {
      return;
    }
    this.isMarkingAll = true;
    forkJoin(unread.map((m) => this.mangaService.markRead(m.id))).subscribe({
      next: (updatedList) => {
        updatedList.forEach((updated) => {
          const local = this.mangaList.find((m) => m.id === updated.id);
          if (local) {
            this.applyMangaUpdate(local, updated);
          }
        });
        this.isMarkingAll = false;
      },
      error: () => {
        this.error = 'Failed to mark all as read. Please try again.';
        this.isMarkingAll = false;
      },
    });
  }

  toggleNotifications(manga: Manga, event: Event): void {
    const checkbox = event.target as HTMLInputElement;
    const newValue = checkbox.checked;

    this.runAction(
      manga.id,
      this.mangaService.updateManga(manga.id, { notificationsEnabled: newValue }),
      (updated) => {
        this.applyMangaUpdate(manga, updated);
      },
      () => {
        checkbox.checked = manga.notificationsEnabled;
        this.actionError[manga.id] = 'Failed to update notifications.';
      },
    );
  }

  incrementChapter(manga: Manga): void {
    if (this.busy[manga.id]) {
      return;
    }

    this.runAction(
      manga.id,
      this.mangaService.updateManga(manga.id, { currentChapter: manga.currentChapter + 1 }),
      (updated) => {
        this.applyMangaUpdate(manga, updated);
      },
      () => {
        this.actionError[manga.id] = 'Failed to update progress.';
      },
    );
  }

  changeReadingStatus(manga: Manga, event: Event): void {
    const select = event.target as HTMLSelectElement;
    const previousStatus = manga.readingStatus;
    const newStatus = select.value;

    if (newStatus === previousStatus || this.busy[manga.id]) {
      select.value = previousStatus;
      return;
    }

    this.runAction(
      manga.id,
      this.mangaService.updateManga(manga.id, { readingStatus: newStatus }),
      (updated) => {
        this.applyMangaUpdate(manga, updated);
      },
      () => {
        select.value = previousStatus;
        this.actionError[manga.id] = 'Failed to update status.';
      },
    );
  }

  readHereUrl(manga: Manga): string | null {
    const sourceUrl = manga.sourceUrl?.trim();
    if (!sourceUrl) {
      return null;
    }
    try {
      const url = new URL(sourceUrl);
      return url.protocol === 'http:' || url.protocol === 'https:' ? sourceUrl : null;
    } catch {
      return null;
    }
  }

  onDelete(manga: Manga): void {
    const confirmed = window.confirm(`Remove "${manga.title}" from your reading list?`);
    if (!confirmed) {
      return;
    }
    this.busy[manga.id] = true;
    this.mangaService.deleteManga(manga.id).subscribe({
      next: () => {
        this.mangaList = this.mangaList.filter((m) => m.id !== manga.id);
      },
      error: () => {
        this.actionError[manga.id] = 'Failed to delete. Please try again.';
        this.busy[manga.id] = false;
      },
    });
  }

  onTestPush(manga: Manga): void {
    this.runAction(
      manga.id,
      this.mangaService.testPush(manga.id),
      () => undefined,
      () => {
        this.actionError[manga.id] = 'Failed to send test push. Please try again.';
      },
    );
  }

  getRelativeDate(dateString: string): string {
    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

    if (diffDays === 0) {
      return 'Latest chapter added today';
    } else if (diffDays === 1) {
      return 'Latest chapter added 1 day ago';
    } else {
      return `Latest chapter added ${diffDays} days ago`;
    }
  }

  private applyMangaUpdate(local: Manga, updated: Manga): void {
    local.sourceUrl = updated.sourceUrl;
    local.mangadexId = updated.mangadexId;
    local.currentChapter = updated.currentChapter;
    local.latestChapter = updated.latestChapter;
    local.coverImageUrl = updated.coverImageUrl;
    local.readingStatus = updated.readingStatus;
    local.latestChapterAt = updated.latestChapterAt;
    local.notificationsEnabled = updated.notificationsEnabled;
    local.lastCheckedAt = updated.lastCheckedAt;
    local.updatedAt = updated.updatedAt;
  }

  private runAction<T>(
    mangaId: string,
    request$: Observable<T>,
    onSuccess: (value: T) => void,
    onError: () => void,
  ): void {
    this.busy[mangaId] = true;
    this.actionError[mangaId] = null;
    request$.subscribe({
      next: (value) => {
        onSuccess(value);
        this.busy[mangaId] = false;
      },
      error: () => {
        onError();
        this.busy[mangaId] = false;
      },
    });
  }
}

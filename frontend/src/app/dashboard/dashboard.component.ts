import { Component, OnInit, DestroyRef, computed, inject, signal } from '@angular/core';
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

  readonly mangaList = signal<Manga[]>([]);
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly actionError = signal<Record<string, string | null>>({});
  readonly busy = signal<Record<string, boolean>>({});
  readonly isMarkingAll = signal(false);
  readonly unreadCount = computed(() => this.mangaList().filter((m) => this.isUnread(m)).length);

  ngOnInit(): void {
    this.loadManga();
  }

  loadManga(): void {
    this.isLoading.set(true);
    this.error.set(null);
    this.mangaService
      .getManga()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (manga) => {
          this.mangaList.set(manga);
          this.isLoading.set(false);
        },
        error: () => {
          this.error.set('Failed to load manga list. Please try again.');
          this.isLoading.set(false);
        },
      });
  }

  isUnread(manga: Manga): boolean {
    return manga.latestChapter > manga.currentChapter;
  }

  /** Toggle a manga between read (caught up) and unread — the latter undoes a mark-read. */
  toggleRead(manga: Manga): void {
    if (this.busy()[manga.id]) {
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
        this.applyMangaUpdate(manga.id, updated);
      },
      () => {
        this.setActionError(manga.id, wasUnread ? 'Failed to mark as read.' : 'Failed to undo.');
      },
    );
  }

  markAllRead(): void {
    const unread = this.mangaList().filter((m) => this.isUnread(m));
    if (this.isMarkingAll() || unread.length === 0) {
      return;
    }
    this.isMarkingAll.set(true);
    forkJoin(unread.map((m) => this.mangaService.markRead(m.id))).subscribe({
      next: (updatedList) => {
        updatedList.forEach((updated) => {
          this.applyMangaUpdate(updated.id, updated);
        });
        this.isMarkingAll.set(false);
      },
      error: () => {
        this.error.set('Failed to mark all as read. Please try again.');
        this.isMarkingAll.set(false);
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
        this.applyMangaUpdate(manga.id, updated);
      },
      () => {
        checkbox.checked = manga.notificationsEnabled;
        this.setActionError(manga.id, 'Failed to update notifications.');
      },
    );
  }

  incrementChapter(manga: Manga): void {
    if (this.busy()[manga.id]) {
      return;
    }

    this.runAction(
      manga.id,
      this.mangaService.updateManga(manga.id, { currentChapter: manga.currentChapter + 1 }),
      (updated) => {
        this.applyMangaUpdate(manga.id, updated);
      },
      () => {
        this.setActionError(manga.id, 'Failed to update progress.');
      },
    );
  }

  changeReadingStatus(manga: Manga, event: Event): void {
    const select = event.target as HTMLSelectElement;
    const previousStatus = manga.readingStatus;
    const newStatus = select.value;

    if (newStatus === previousStatus || this.busy()[manga.id]) {
      select.value = previousStatus;
      return;
    }

    this.runAction(
      manga.id,
      this.mangaService.updateManga(manga.id, { readingStatus: newStatus }),
      (updated) => {
        this.applyMangaUpdate(manga.id, updated);
      },
      () => {
        select.value = previousStatus;
        this.setActionError(manga.id, 'Failed to update status.');
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
    this.setBusy(manga.id, true);
    this.mangaService.deleteManga(manga.id).subscribe({
      next: () => {
        this.mangaList.update((list) => list.filter((m) => m.id !== manga.id));
      },
      error: () => {
        this.setActionError(manga.id, 'Failed to delete. Please try again.');
        this.setBusy(manga.id, false);
      },
    });
  }

  onTestPush(manga: Manga): void {
    this.runAction(
      manga.id,
      this.mangaService.testPush(manga.id),
      () => undefined,
      () => {
        this.setActionError(manga.id, 'Failed to send test push. Please try again.');
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

  private setBusy(id: string, value: boolean): void {
    this.busy.update((b) => ({ ...b, [id]: value }));
  }

  private setActionError(id: string, msg: string | null): void {
    this.actionError.update((e) => ({ ...e, [id]: msg }));
  }

  private applyMangaUpdate(id: string, updated: Manga): void {
    this.mangaList.update((list) =>
      list.map((m) =>
        m.id === id
          ? {
              ...m,
              sourceUrl: updated.sourceUrl,
              mangadexId: updated.mangadexId,
              currentChapter: updated.currentChapter,
              latestChapter: updated.latestChapter,
              coverImageUrl: updated.coverImageUrl,
              readingStatus: updated.readingStatus,
              latestChapterAt: updated.latestChapterAt,
              notificationsEnabled: updated.notificationsEnabled,
              lastCheckedAt: updated.lastCheckedAt,
              updatedAt: updated.updatedAt,
            }
          : m,
      ),
    );
  }

  private runAction<T>(
    mangaId: string,
    request$: Observable<T>,
    onSuccess: (value: T) => void,
    onError: () => void,
  ): void {
    this.setBusy(mangaId, true);
    this.setActionError(mangaId, null);
    request$.subscribe({
      next: (value) => {
        onSuccess(value);
        this.setBusy(mangaId, false);
      },
      error: () => {
        onError();
        this.setBusy(mangaId, false);
      },
    });
  }
}

import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';

import { AddMangaFormComponent } from '../add-manga/add-manga-form.component';
import { Manga, MangaService } from '../services/manga.service';
import { relativeTime } from '../shared/relative-time';

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

  checkedLabel(manga: Manga): string {
    return relativeTime(manga.lastCheckedAt);
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
    this.busy[manga.id] = true;
    this.actionError[manga.id] = null;
    request$.subscribe({
      next: (updated) => {
        manga.currentChapter = updated.currentChapter;
        this.busy[manga.id] = false;
      },
      error: () => {
        this.actionError[manga.id] = wasUnread ? 'Failed to mark as read.' : 'Failed to undo.';
        this.busy[manga.id] = false;
      },
    });
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
            local.currentChapter = updated.currentChapter;
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

    this.busy[manga.id] = true;
    this.mangaService.updateManga(manga.id, { notificationsEnabled: newValue }).subscribe({
      next: (updated) => {
        manga.notificationsEnabled = updated.notificationsEnabled;
        this.busy[manga.id] = false;
      },
      error: () => {
        checkbox.checked = manga.notificationsEnabled;
        this.actionError[manga.id] = 'Failed to update notifications.';
        this.busy[manga.id] = false;
      },
    });
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
}

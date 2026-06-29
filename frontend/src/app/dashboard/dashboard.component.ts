import { ChangeDetectorRef, Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';

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
  private readonly changeDetector = inject(ChangeDetectorRef);
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
          this.changeDetector.detectChanges();
        },
        error: () => {
          this.error = 'Failed to load manga list. Please try again.';
          this.isLoading = false;
          this.changeDetector.detectChanges();
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
    this.busy[manga.id] = true;
    this.actionError[manga.id] = null;
    request$.subscribe({
      next: (updated) => {
        manga.currentChapter = updated.currentChapter;
        this.busy[manga.id] = false;
        this.changeDetector.detectChanges();
      },
      error: () => {
        this.actionError[manga.id] = wasUnread ? 'Failed to mark as read.' : 'Failed to undo.';
        this.busy[manga.id] = false;
        this.changeDetector.detectChanges();
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
        this.changeDetector.detectChanges();
      },
      error: () => {
        this.error = 'Failed to mark all as read. Please try again.';
        this.isMarkingAll = false;
        this.changeDetector.detectChanges();
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
        this.changeDetector.detectChanges();
      },
      error: () => {
        checkbox.checked = manga.notificationsEnabled;
        this.actionError[manga.id] = 'Failed to update notifications.';
        this.busy[manga.id] = false;
        this.changeDetector.detectChanges();
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
        this.changeDetector.detectChanges();
      },
      error: () => {
        this.actionError[manga.id] = 'Failed to delete. Please try again.';
        this.busy[manga.id] = false;
        this.changeDetector.detectChanges();
      },
    });
  }

  onTestPush(manga: Manga): void {
    this.busy[manga.id] = true;
    this.actionError[manga.id] = null;
    this.mangaService.testPush(manga.id).subscribe({
      next: () => {
        this.busy[manga.id] = false;
        this.changeDetector.detectChanges();
      },
      error: () => {
        this.actionError[manga.id] = 'Failed to send test push. Please try again.';
        this.busy[manga.id] = false;
        this.changeDetector.detectChanges();
      },
    });
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
}

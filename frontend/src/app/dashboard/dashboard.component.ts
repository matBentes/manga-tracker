import { Component, OnInit, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

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

  mangaList: Manga[] = [];
  isLoading = false;
  error: string | null = null;
  savingState: Record<string, boolean> = {};
  updateError: Record<string, string | null> = {};
  deletingState: Record<string, boolean> = {};
  togglingNotifications: Record<string, boolean> = {};

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

  hasUnreadChapters(manga: Manga): boolean {
    return manga.latestChapter > manga.currentChapter;
  }

  onChapterChange(manga: Manga, event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = parseInt(input.value, 10);

    if (isNaN(value) || value < 0 || value > manga.latestChapter) {
      this.updateError[manga.id] = `Chapter must be between 0 and ${manga.latestChapter}.`;
      input.value = String(manga.currentChapter);
      return;
    }

    if (value === manga.currentChapter) {
      this.updateError[manga.id] = null;
      return;
    }

    this.updateError[manga.id] = null;
    this.savingState[manga.id] = true;

    this.mangaService.updateManga(manga.id, { currentChapter: value }).subscribe({
      next: (updated) => {
        manga.currentChapter = updated.currentChapter;
        this.savingState[manga.id] = false;
      },
      error: () => {
        this.updateError[manga.id] = 'Failed to save chapter. Please try again.';
        this.savingState[manga.id] = false;
        input.value = String(manga.currentChapter);
      },
    });
  }

  onNotificationsToggle(manga: Manga, event: Event): void {
    const checkbox = event.target as HTMLInputElement;
    const newValue = checkbox.checked;

    this.togglingNotifications[manga.id] = true;
    this.mangaService.updateManga(manga.id, { notificationsEnabled: newValue }).subscribe({
      next: (updated) => {
        manga.notificationsEnabled = updated.notificationsEnabled;
        this.togglingNotifications[manga.id] = false;
      },
      error: () => {
        checkbox.checked = manga.notificationsEnabled;
        this.updateError[manga.id] = 'Failed to update notification setting.';
        this.togglingNotifications[manga.id] = false;
      },
    });
  }

  onDelete(manga: Manga): void {
    const confirmed = window.confirm(`Remove "${manga.title}" from your reading list?`);
    if (!confirmed) return;

    this.deletingState[manga.id] = true;

    this.mangaService.deleteManga(manga.id).subscribe({
      next: () => {
        this.mangaList = this.mangaList.filter((m) => m.id !== manga.id);
        this.deletingState[manga.id] = false;
      },
      error: () => {
        this.updateError[manga.id] = 'Failed to delete. Please try again.';
        this.deletingState[manga.id] = false;
      },
    });
  }
}

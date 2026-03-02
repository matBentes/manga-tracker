import { Component, OnInit, inject } from '@angular/core';

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

  mangaList: Manga[] = [];
  isLoading = false;
  error: string | null = null;

  ngOnInit(): void {
    this.loadManga();
  }

  loadManga(): void {
    this.isLoading = true;
    this.error = null;
    this.mangaService.getManga().subscribe({
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
}

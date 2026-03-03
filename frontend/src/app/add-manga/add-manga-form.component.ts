import { Component, EventEmitter, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { Manga, MangaService } from '../services/manga.service';

@Component({
  selector: 'app-add-manga-form',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './add-manga-form.component.html',
  styleUrl: './add-manga-form.component.scss',
})
export class AddMangaFormComponent {
  private readonly mangaService = inject(MangaService);

  @Output() mangaAdded = new EventEmitter<Manga>();

  url = '';
  isSubmitting = false;
  error: string | null = null;
  successMessage: string | null = null;
  urlValidationError: string | null = null;

  private static readonly URL_PATTERN = /^https?:\/\/.+/i;

  get isUrlValid(): boolean {
    const trimmed = this.url.trim();
    return trimmed.length > 0 && AddMangaFormComponent.URL_PATTERN.test(trimmed);
  }

  onUrlChange(): void {
    this.error = null;
    this.successMessage = null;
    const trimmed = this.url.trim();
    if (trimmed.length === 0) {
      this.urlValidationError = null;
    } else if (!AddMangaFormComponent.URL_PATTERN.test(trimmed)) {
      this.urlValidationError = 'Please enter a valid URL starting with http:// or https://';
    } else {
      this.urlValidationError = null;
    }
  }

  onSubmit(): void {
    const trimmedUrl = this.url.trim();
    if (!trimmedUrl || !this.isUrlValid) return;

    this.isSubmitting = true;
    this.error = null;
    this.successMessage = null;

    this.mangaService.addManga(trimmedUrl).subscribe({
      next: (manga) => {
        this.successMessage = `"${manga.title}" has been added to your reading list.`;
        this.url = '';
        this.isSubmitting = false;
        this.mangaAdded.emit(manga);
      },
      error: (err: HttpErrorResponse) => {
        this.isSubmitting = false;
        if (err.status === 400) {
          this.error = 'This URL is not from a supported manga source.';
        } else if (err.status === 409) {
          this.error = 'This manga is already in your reading list.';
        } else if (err.status === 422) {
          this.error = 'Could not extract manga data from that URL. Please try again.';
        } else {
          this.error = 'An unexpected error occurred. Please try again.';
        }
      },
    });
  }
}

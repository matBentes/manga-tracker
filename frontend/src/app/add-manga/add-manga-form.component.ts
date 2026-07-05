import {
  Component,
  DestroyRef,
  EventEmitter,
  OnInit,
  Output,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, debounceTime, distinctUntilChanged, map, of, Subject, switchMap } from 'rxjs';

import { AddMangaPayload, Manga, MangaSearchResult, MangaService } from '../services/manga.service';

interface ReadingStatusOption {
  value: string;
  label: string;
}

@Component({
  selector: 'app-add-manga-form',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './add-manga-form.component.html',
  styleUrl: './add-manga-form.component.scss',
})
export class AddMangaFormComponent implements OnInit {
  private readonly mangaService = inject(MangaService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchTerms = new Subject<string>();

  @Output() mangaAdded = new EventEmitter<Manga>();

  readonly minimumSearchLength = 2;
  readonly statusOptions: ReadingStatusOption[] = [
    { value: 'READING', label: 'Reading' },
    { value: 'COMPLETED', label: 'Completed' },
    { value: 'ON_HOLD', label: 'On hold' },
    { value: 'DROPPED', label: 'Dropped' },
    { value: 'PLAN_TO_READ', label: 'Plan to read' },
  ];

  searchQuery = '';
  readonly searchResults = signal<MangaSearchResult[]>([]);
  readonly selectedResult = signal<MangaSearchResult | null>(null);
  sourceUrl = '';
  currentChapter: number | null = null;
  selectedStatus = 'READING';
  readonly isSearching = signal(false);
  readonly isSubmitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly searchError = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly sourceUrlValidationError = signal<string | null>(null);
  readonly chapterValidationError = signal<string | null>(null);
  readonly hasSearched = signal(false);
  readonly canSubmit = computed(
    () =>
      this.selectedResult() !== null &&
      !this.isSubmitting() &&
      this.sourceUrlValidationError() === null &&
      this.chapterValidationError() === null,
  );

  ngOnInit(): void {
    this.searchTerms
      .pipe(
        map((query) => query.trim()),
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((query) => {
          this.searchError.set(null);
          this.error.set(null);
          this.successMessage.set(null);

          if (query.length < this.minimumSearchLength) {
            this.hasSearched.set(false);
            this.isSearching.set(false);
            return of([]);
          }

          this.hasSearched.set(true);
          this.isSearching.set(true);
          return this.mangaService.searchManga(query).pipe(
            catchError((err: HttpErrorResponse) => {
              this.searchError.set(this.mapSearchError(err));
              return of([]);
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((results) => {
        // A selection may have been made while this response was in flight; don't
        // reopen the results list underneath the selected-title panel.
        if (this.selectedResult() !== null) {
          this.isSearching.set(false);
          return;
        }
        this.searchResults.set(results);
        this.isSearching.set(false);
      });
  }

  onSearchQueryChange(query: string): void {
    const selectedResult = this.selectedResult();
    if (selectedResult && query !== selectedResult.title) {
      this.selectedResult.set(null);
    }
    this.searchTerms.next(query);
  }

  selectResult(result: MangaSearchResult): void {
    this.selectedResult.set(result);
    this.searchQuery = result.title;
    this.searchResults.set([]);
    this.isSearching.set(false);
    this.searchError.set(null);
    this.error.set(null);
    this.successMessage.set(null);
  }

  onSourceUrlChange(): void {
    this.error.set(null);
    this.successMessage.set(null);
    const trimmed = this.sourceUrl.trim();
    if (trimmed.length === 0) {
      this.sourceUrlValidationError.set(null);
    } else if (!this.isHttpUrl(trimmed)) {
      this.sourceUrlValidationError.set('Read-here URL must start with http:// or https://');
    } else {
      this.sourceUrlValidationError.set(null);
    }
  }

  onChapterChange(): void {
    this.error.set(null);
    this.successMessage.set(null);
    if (
      this.currentChapter === null ||
      (Number.isInteger(this.currentChapter) && this.currentChapter >= 0)
    ) {
      this.chapterValidationError.set(null);
    } else {
      this.chapterValidationError.set('Starting chapter must be a whole number of 0 or higher.');
    }
  }

  onSubmit(): void {
    const selectedResult = this.selectedResult();
    if (!selectedResult || !this.canSubmit()) return;

    this.isSubmitting.set(true);
    this.error.set(null);
    this.successMessage.set(null);

    this.mangaService.addManga(this.buildPayload(selectedResult)).subscribe({
      next: (manga) => {
        this.successMessage.set(`"${manga.title}" has been added to your reading list.`);
        this.resetForm();
        this.isSubmitting.set(false);
        this.mangaAdded.emit(manga);
      },
      error: (err: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        this.error.set(this.mapAddError(err));
      },
    });
  }

  descriptionSnippet(result: MangaSearchResult): string {
    const description = result.description?.trim();
    if (!description) {
      return 'No synopsis available.';
    }
    return description.length > 220 ? `${description.slice(0, 217)}...` : description;
  }

  private buildPayload(result: MangaSearchResult): AddMangaPayload {
    const payload: AddMangaPayload = {
      mangaDexId: result.mangaDexId,
      readingStatus: this.selectedStatus,
    };
    const trimmedSourceUrl = this.sourceUrl.trim();
    if (trimmedSourceUrl) {
      payload.sourceUrl = trimmedSourceUrl;
    }
    if (this.currentChapter !== null) {
      payload.currentChapter = this.currentChapter;
    }
    return payload;
  }

  private resetForm(): void {
    this.searchQuery = '';
    this.searchResults.set([]);
    this.selectedResult.set(null);
    this.sourceUrl = '';
    this.currentChapter = null;
    this.selectedStatus = 'READING';
    this.sourceUrlValidationError.set(null);
    this.chapterValidationError.set(null);
    this.searchError.set(null);
    this.hasSearched.set(false);
  }

  private isHttpUrl(value: string): boolean {
    try {
      const url = new URL(value);
      return url.protocol === 'http:' || url.protocol === 'https:';
    } catch {
      return false;
    }
  }

  private mapSearchError(err: HttpErrorResponse): string {
    if (err.status === 400) {
      return 'Enter a valid MangaDex search query.';
    }
    if (err.status === 429) {
      return 'MangaDex search is rate-limited. Please wait a moment and try again.';
    }
    if (err.status === 502) {
      return 'MangaDex is unavailable right now. Please try again later.';
    }
    return 'Search failed. Please try again.';
  }

  private mapAddError(err: HttpErrorResponse): string {
    if (err.status === 400) {
      return 'Check the selected title, read-here URL, starting chapter, and status.';
    }
    if (err.status === 409) {
      return 'This MangaDex title is already in your reading list.';
    }
    if (err.status === 429) {
      return 'Adding manga is rate-limited. Please wait a moment and try again.';
    }
    if (err.status === 502) {
      return 'MangaDex is unavailable right now. Please try again later.';
    }
    return 'An unexpected error occurred. Please try again.';
  }
}

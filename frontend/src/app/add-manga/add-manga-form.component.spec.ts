import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AddMangaFormComponent } from './add-manga-form.component';
import { Manga, MangaSearchResult, MangaService } from '../services/manga.service';

const searchResult: MangaSearchResult = {
  mangaDexId: '22222222-2222-2222-2222-222222222222',
  title: 'Blue Lock',
  description: 'A soccer manga.',
  coverImageUrl: 'https://uploads.mangadex.org/covers/blue-lock/cover.512.jpg',
};

const addedManga: Manga = {
  id: '11111111-1111-1111-1111-111111111111',
  title: 'Blue Lock',
  sourceUrl: 'https://example.com/read/blue-lock',
  mangadexId: searchResult.mangaDexId,
  currentChapter: 12,
  latestChapter: 20,
  coverImageUrl: searchResult.coverImageUrl,
  readingStatus: 'ON_HOLD',
  latestChapterAt: null,
  notificationsEnabled: true,
  lastCheckedAt: null,
  createdAt: '2024-01-01T00:00:00',
  updatedAt: '2024-01-01T00:00:00',
};

function createComponent(addManga = vi.fn(() => of(addedManga))) {
  const mangaService = {
    searchManga: vi.fn(() => of([searchResult])),
    addManga,
  };

  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    imports: [AddMangaFormComponent],
    providers: [{ provide: MangaService, useValue: mangaService }],
  });

  const fixture = TestBed.createComponent(AddMangaFormComponent);
  fixture.detectChanges();
  return { component: fixture.componentInstance, mangaService };
}

describe('AddMangaFormComponent', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('submits the selected MangaDex title with optional progress fields', () => {
    const { component, mangaService } = createComponent();
    let emitted: Manga | null = null;
    component.mangaAdded.subscribe((manga) => {
      emitted = manga;
    });

    component.selectResult(searchResult);
    component.sourceUrl = 'https://example.com/read/blue-lock';
    component.currentChapter = 12;
    component.selectedStatus = 'ON_HOLD';
    component.onSubmit();

    expect(mangaService.addManga).toHaveBeenCalledWith({
      mangaDexId: searchResult.mangaDexId,
      sourceUrl: 'https://example.com/read/blue-lock',
      currentChapter: 12,
      readingStatus: 'ON_HOLD',
    });
    expect(emitted).toEqual(addedManga);
    expect(component.successMessage()).toContain('"Blue Lock" has been added');
  });

  it('does not submit when the optional read-here URL is not http(s)', () => {
    const { component, mangaService } = createComponent();

    component.selectResult(searchResult);
    component.sourceUrl = 'ftp://example.com/read/blue-lock';
    component.onSourceUrlChange();
    component.onSubmit();

    expect(component.sourceUrlValidationError()).toContain('http:// or https://');
    expect(mangaService.addManga).not.toHaveBeenCalled();
  });

  it('ignores a stale search response arriving after a result was selected', () => {
    vi.useFakeTimers();
    try {
      const pendingSearch = new Subject<MangaSearchResult[]>();
      const { component, mangaService } = createComponent();
      mangaService.searchManga.mockReturnValue(pendingSearch);

      component.onSearchQueryChange('blue lock');
      vi.advanceTimersByTime(300); // debounce fires, request now in flight

      component.selectResult(searchResult); // user picks from an earlier batch
      pendingSearch.next([searchResult]); // stale response lands afterwards
      pendingSearch.complete();

      expect(component.searchResults()).toEqual([]);
      expect(component.isSearching()).toBe(false);
      expect(component.selectedResult()).toEqual(searchResult);
    } finally {
      vi.useRealTimers();
    }
  });

  it('surfaces add rate limits distinctly', () => {
    const addManga = vi.fn(() =>
      throwError(() => new HttpErrorResponse({ status: 429, statusText: 'Too Many Requests' })),
    );
    const { component } = createComponent(addManga);

    component.selectResult(searchResult);
    component.onSubmit();

    expect(component.error()).toContain('rate-limited');
  });
});

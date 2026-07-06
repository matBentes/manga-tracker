import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { DashboardComponent } from './dashboard.component';
import { Manga, MangaService } from '../services/manga.service';

function createManga(overrides: Partial<Manga> = {}): Manga {
  return {
    id: '11111111-1111-1111-1111-111111111111',
    title: 'Blue Lock',
    sourceUrl: null,
    mangadexId: '22222222-2222-2222-2222-222222222222',
    currentChapter: 3,
    latestChapter: 10,
    coverImageUrl: null,
    readingStatus: 'READING',
    latestChapterAt: null,
    notificationsEnabled: true,
    lastCheckedAt: null,
    createdAt: '2024-01-01T00:00:00',
    updatedAt: '2024-01-01T00:00:00',
    ...overrides,
  };
}

function createComponent(
  updateManga = vi.fn((_id: string, patch: Partial<Manga>) => of(createManga(patch))),
) {
  const initialManga = createManga();
  const mangaService = {
    getManga: vi.fn(() => of([initialManga])),
    updateManga,
    markRead: vi.fn(),
    markUnread: vi.fn(),
    deleteManga: vi.fn(),
    testPush: vi.fn(),
    searchManga: vi.fn(),
    addManga: vi.fn(),
  };

  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    imports: [DashboardComponent],
    providers: [{ provide: MangaService, useValue: mangaService }],
  });

  const fixture = TestBed.createComponent(DashboardComponent);
  fixture.detectChanges();
  return { component: fixture.componentInstance, mangaService };
}

describe('DashboardComponent', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('increments current chapter through PATCH', () => {
    const { component, mangaService } = createComponent();
    const localManga = component.mangaList()[0];

    component.incrementChapter(localManga);

    expect(mangaService.updateManga).toHaveBeenCalledWith(localManga.id, { currentChapter: 4 });
    expect(component.mangaList()[0].currentChapter).toBe(4);
  });

  it('updates reading status through PATCH', () => {
    const { component, mangaService } = createComponent();
    const localManga = component.mangaList()[0];
    const select = document.createElement('select');
    // A <select> ignores value assignments unless a matching <option> exists.
    const option = document.createElement('option');
    option.value = 'COMPLETED';
    select.appendChild(option);
    select.value = 'COMPLETED';

    component.changeReadingStatus(localManga, { target: select } as unknown as Event);

    expect(mangaService.updateManga).toHaveBeenCalledWith(localManga.id, {
      readingStatus: 'COMPLETED',
    });
    expect(component.mangaList()[0].readingStatus).toBe('COMPLETED');
  });

  it('only returns safe http(s) read-here URLs', () => {
    const { component } = createComponent();

    expect(component.readHereUrl(createManga({ sourceUrl: null }))).toBeNull();
    expect(component.readHereUrl(createManga({ sourceUrl: 'javascript:alert(1)' }))).toBeNull();
    expect(component.readHereUrl(createManga({ sourceUrl: 'https://example.com/read' }))).toBe(
      'https://example.com/read',
    );
  });
});

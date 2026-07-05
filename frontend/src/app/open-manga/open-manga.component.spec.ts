import { TestBed } from '@angular/core/testing';
import { render } from '@testing-library/angular';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { OpenMangaComponent, REDIRECT_TO_URL } from './open-manga.component';
import { Manga, MangaService } from '../services/manga.service';

const manga: Manga = {
  id: '11111111-1111-1111-1111-111111111111',
  title: 'Blue Lock',
  sourceUrl: 'https://sakuramangas.org/obras/blue-lock/',
  mangadexId: '22222222-2222-2222-2222-222222222222',
  currentChapter: 169,
  latestChapter: 169,
  coverImageUrl: null,
  readingStatus: 'READING',
  latestChapterAt: null,
  notificationsEnabled: true,
  lastCheckedAt: null,
  createdAt: '2024-01-01T00:00:00',
  updatedAt: '2024-01-01T00:00:00',
};

function activatedRoute(id: string | null, sourceUrl: string | null = null): ActivatedRoute {
  return {
    snapshot: {
      paramMap: { get: () => id },
      queryParamMap: { get: () => sourceUrl },
    },
  } as unknown as ActivatedRoute;
}

async function renderComponent(route: ActivatedRoute, markRead = vi.fn(() => of(manga))) {
  TestBed.resetTestingModule();
  const redirect = vi.fn();
  await render(OpenMangaComponent, {
    providers: [
      { provide: ActivatedRoute, useValue: route },
      { provide: MangaService, useValue: { markRead } },
      { provide: REDIRECT_TO_URL, useValue: redirect },
    ],
  });
  return { redirect, markRead };
}

describe('OpenMangaComponent', () => {
  it('redirects to the owned manga source URL returned by markRead', async () => {
    const { redirect, markRead } = await renderComponent(
      activatedRoute(manga.id, 'https://evil.example/phish'),
    );

    expect(markRead).toHaveBeenCalledWith(manga.id);
    expect(redirect).toHaveBeenCalledWith(manga.sourceUrl);
  });

  it('does not redirect to the query-string URL when markRead fails', async () => {
    const { redirect } = await renderComponent(
      activatedRoute(manga.id, 'https://evil.example/phish'),
      vi.fn(() => throwError(() => new Error('not found'))),
    );

    expect(redirect).toHaveBeenCalledWith('/');
  });

  it('falls back to the dashboard when the manga has no source URL', async () => {
    const { redirect } = await renderComponent(
      activatedRoute(manga.id),
      vi.fn(() => of({ ...manga, sourceUrl: null })),
    );

    expect(redirect).toHaveBeenCalledWith('/');
  });

  it('falls back to the dashboard for a non-http source URL', async () => {
    const { redirect } = await renderComponent(
      activatedRoute(manga.id),
      vi.fn(() => of({ ...manga, sourceUrl: 'javascript:alert(1)' })),
    );

    expect(redirect).toHaveBeenCalledWith('/');
  });
});

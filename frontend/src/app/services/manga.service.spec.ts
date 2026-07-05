import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { AddMangaPayload, MangaService } from './manga.service';

describe('MangaService', () => {
  let service: MangaService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [MangaService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MangaService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('searchManga calls the MangaDex search endpoint with the query', () => {
    service.searchManga('blue lock').subscribe();

    const req = httpMock.expectOne(
      (request) =>
        request.method === 'GET' &&
        request.url === '/api/manga/search' &&
        request.params.get('q') === 'blue lock',
    );
    req.flush([]);
  });

  it('addManga posts the MangaDex add payload', () => {
    const payload: AddMangaPayload = {
      mangaDexId: '22222222-2222-2222-2222-222222222222',
      sourceUrl: 'https://example.com/read/blue-lock',
      currentChapter: 12,
      readingStatus: 'ON_HOLD',
    };

    service.addManga(payload).subscribe();

    const req = httpMock.expectOne('/api/manga');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({});
  });

  it('updateManga patches progress, latest chapter, status, and notifications', () => {
    service
      .updateManga('11111111-1111-1111-1111-111111111111', {
        notificationsEnabled: false,
        currentChapter: 13,
        latestChapter: 20,
        readingStatus: 'COMPLETED',
      })
      .subscribe();

    const req = httpMock.expectOne('/api/manga/11111111-1111-1111-1111-111111111111');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({
      notificationsEnabled: false,
      currentChapter: 13,
      latestChapter: 20,
      readingStatus: 'COMPLETED',
    });
    req.flush({});
  });
});

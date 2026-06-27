import { Component, InjectionToken, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { MangaService } from '../services/manga.service';

export const REDIRECT_TO_URL = new InjectionToken<(url: string) => void>('REDIRECT_TO_URL', {
  providedIn: 'root',
  factory: () => (url: string) => {
    window.location.href = url;
  },
});

/**
 * Landing route the push notification opens. It marks the manga as read (the user is opening it, so
 * they're caught up) and then redirects to the manga's source page. Marking read is best-effort:
 * the redirect happens regardless so a failed API call never traps the user here.
 */
@Component({
  selector: 'app-open-manga',
  standalone: true,
  imports: [],
  template: `<p class="open-status">Opening…</p>`,
  styles: [
    `
      .open-status {
        padding: 2rem;
        text-align: center;
        color: #555;
      }
    `,
  ],
})
export class OpenMangaComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly mangaService = inject(MangaService);
  private readonly redirectToUrl = inject(REDIRECT_TO_URL);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');

    if (!id) {
      this.redirect('/');
      return;
    }
    this.mangaService.markRead(id).subscribe({
      next: (manga) => this.redirect(manga.sourceUrl),
      error: () => this.redirect('/'),
    });
  }

  private redirect(url: string): void {
    this.redirectToUrl(url);
  }
}

import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { MangaService } from '../services/manga.service';

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

  private static readonly SOURCE_URL_PARAM = 'u';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    const sourceUrl = this.route.snapshot.queryParamMap.get(OpenMangaComponent.SOURCE_URL_PARAM);

    if (!id) {
      this.redirect(sourceUrl);
      return;
    }
    this.mangaService.markRead(id).subscribe({
      next: () => this.redirect(sourceUrl),
      error: () => this.redirect(sourceUrl),
    });
  }

  private redirect(sourceUrl: string | null): void {
    window.location.href = sourceUrl ?? '/';
  }
}

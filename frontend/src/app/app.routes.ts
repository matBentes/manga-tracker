import { Routes } from '@angular/router';

import { DashboardComponent } from './dashboard/dashboard.component';
import { OpenMangaComponent } from './open-manga/open-manga.component';
import { SettingsComponent } from './settings/settings.component';

export const routes: Routes = [
  { path: '', component: DashboardComponent },
  { path: 'settings', component: SettingsComponent },
  // Push-notification landing: mark the manga read, then redirect to its source page.
  { path: 'open/:id', component: OpenMangaComponent },
];

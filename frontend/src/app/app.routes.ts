import { Routes } from '@angular/router';

import { DashboardComponent } from './dashboard/dashboard.component';
import { authGuard } from './guards/auth.guard';
import { LoginComponent } from './login/login.component';
import { OpenMangaComponent } from './open-manga/open-manga.component';
import { SettingsComponent } from './settings/settings.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: '', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'settings', component: SettingsComponent, canActivate: [authGuard] },
  // Push-notification landing: mark the manga read, then redirect to its source page.
  { path: 'open/:id', component: OpenMangaComponent, canActivate: [authGuard] },
];

import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

/**
 * Application routes.
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/dashboard/dashboard.component').then(
        (m) => m.DashboardComponent
      ),
    canActivate: [authGuard],
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./pages/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'plugins',
    loadComponent: () =>
      import('./pages/plugins/plugins.component').then(
        (m) => m.PluginsComponent
      ),
    canActivate: [authGuard],
  },
  {
    path: 'plugins/:key',
    loadComponent: () =>
      import('./pages/plugin-detail/plugin-detail.component').then(
        (m) => m.PluginDetailComponent
      ),
    canActivate: [authGuard],
  },
  {
    path: 'users',
    loadComponent: () =>
      import('./pages/users/users.component').then((m) => m.UsersComponent),
    canActivate: [authGuard],
  },
  {
    path: 'audit',
    loadComponent: () =>
      import('./pages/audit/audit.component').then((m) => m.AuditComponent),
    canActivate: [authGuard],
  },
  {
    path: 'settings',
    loadComponent: () =>
      import('./pages/settings/settings.component').then(
        (m) => m.SettingsComponent
      ),
    canActivate: [authGuard],
  },
  {
    path: '**',
    redirectTo: '',
  },
];

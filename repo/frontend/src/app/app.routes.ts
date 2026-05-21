import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full',
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./dashboard/dashboard.component').then(
        (m) => m.DashboardComponent
      ),
    canActivate: [authGuard],
  },
  {
    path: 'sessions',
    loadComponent: () =>
      import('./sessions/session-list.component').then(
        (m) => m.SessionListComponent
      ),
    canActivate: [authGuard],
    data: { roles: ['STUDENT'] },
  },
  {
    path: 'sessions/new',
    loadComponent: () =>
      import('./sessions/session-capture.component').then(
        (m) => m.SessionCaptureComponent
      ),
    canActivate: [authGuard],
    data: { roles: ['STUDENT'] },
  },
  {
    path: 'sessions/:id',
    loadComponent: () =>
      import('./sessions/session-capture.component').then(
        (m) => m.SessionCaptureComponent
      ),
    canActivate: [authGuard],
    data: { roles: ['STUDENT'] },
  },
  {
    path: 'analytics',
    loadComponent: () =>
      import('./analytics/analytics-dashboard.component').then(
        (m) => m.AnalyticsDashboardComponent
      ),
    canActivate: [authGuard],
    data: { roles: ['FACULTY_MENTOR', 'CORPORATE_MENTOR', 'ADMINISTRATOR'] },
  },
  {
    path: 'reports',
    loadComponent: () =>
      import('./reports/reports-center.component').then(
        (m) => m.ReportsCenterComponent
      ),
    canActivate: [authGuard],
    data: { roles: ['FACULTY_MENTOR', 'CORPORATE_MENTOR', 'ADMINISTRATOR'] },
  },
  {
    path: 'admin/users',
    loadComponent: () =>
      import('./admin/user-management.component').then(
        (m) => m.UserManagementComponent
      ),
    canActivate: [authGuard],
    data: { roles: ['ADMINISTRATOR'] },
  },
  {
    path: 'admin/backup',
    loadComponent: () =>
      import('./admin/backup-admin.component').then(
        (m) => m.BackupAdminComponent
      ),
    canActivate: [authGuard],
    data: { roles: ['ADMINISTRATOR'] },
  },
  {
    path: 'governance',
    loadComponent: () =>
      import('./governance/governance.component').then(
        (m) => m.GovernanceComponent
      ),
    canActivate: [authGuard],
    data: { roles: ['ADMINISTRATOR'] },
  },
  {
    path: '**',
    redirectTo: 'dashboard',
  },
];

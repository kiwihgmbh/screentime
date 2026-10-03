import { Routes } from '@angular/router';
import { parentOnly, signedIn } from './core/guards';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./login/login').then((m) => m.LoginComponent),
    title: 'Sign in',
  },
  {
    path: '',
    canActivate: [signedIn],
    loadComponent: () => import('./child/dashboard/dashboard').then((m) => m.DashboardComponent),
    title: 'Screentime',
  },
  {
    path: 'history',
    canActivate: [signedIn],
    loadComponent: () => import('./child/history/history').then((m) => m.HistoryComponent),
    title: 'Past weeks',
  },
  {
    path: 'rules',
    canActivate: [signedIn],
    loadComponent: () => import('./child/rules/rules').then((m) => m.RulesComponent),
    title: 'The rules',
  },
  {
    path: 'parent',
    canActivate: [signedIn, parentOnly],
    children: [
      {
        path: 'week',
        loadComponent: () => import('./parent/week/week').then((m) => m.ParentWeekComponent),
        title: 'The week',
      },
      {
        path: 'check',
        loadComponent: () => import('./parent/check/check').then((m) => m.CheckComponent),
        title: 'The weekly check',
      },
      {
        path: 'settings',
        loadComponent: () => import('./parent/settings/settings').then((m) => m.SettingsComponent),
        title: 'Settings',
      },
      {
        path: 'checklist',
        loadComponent: () =>
          import('./parent/checklist/checklist').then((m) => m.ChecklistComponent),
        title: 'Checklist',
      },
      {
        path: 'users',
        loadComponent: () => import('./parent/users/users').then((m) => m.UsersComponent),
        title: 'Accounts',
      },
      { path: '', redirectTo: 'week', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: '' },
];

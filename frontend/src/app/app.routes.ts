import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./features/shell/shell.component').then(m => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'map' },
      {
        path: 'list',
        loadComponent: () => import('./features/toilets-list/toilets-list.component').then(m => m.ToiletsListComponent)
      },
      {
        path: 'map',
        loadComponent: () => import('./features/toilets-map/toilets-map.component').then(m => m.ToiletsMapComponent)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];

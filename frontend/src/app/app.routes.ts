import { Routes } from '@angular/router';
import { guestGuard } from './core/guards/guest.guard';
import { adminGuard } from './core/guards/admin.guard';

export const routes: Routes = [
  {
    path: 'admin',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: '',
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
      },
      {
        path: 'log',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/audit-log/audit-log.component').then(m => m.AuditLogComponent)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];

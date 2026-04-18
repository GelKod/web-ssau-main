import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'tasks/new',
    canActivate: [authGuard],
    data: { create: true },
    loadComponent: () => import('./edit-task/edit-task.component').then((m) => m.EditTaskComponent),
  },
  {
    path: 'tasks/:taskId',
    canActivate: [authGuard],
    loadComponent: () => import('./edit-task/edit-task.component').then((m) => m.EditTaskComponent),
  },
  {
    path: 'tasks',
    canActivate: [authGuard],
    loadComponent: () => import('./task-list/task-list.component').then((m) => m.TaskListComponent),
  },
  { path: '', pathMatch: 'full', redirectTo: 'tasks' },
];

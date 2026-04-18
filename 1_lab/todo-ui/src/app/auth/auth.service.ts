import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, Observable, tap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { CurrentUser } from '../models/user.model';

const USER_KEY = 'todo_username';
const PASS_KEY = 'todo_password';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly currentUser = signal<CurrentUser | null>(null);

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {}

  hasStoredCredentials(): boolean {
    return !!sessionStorage.getItem(USER_KEY) && !!sessionStorage.getItem(PASS_KEY);
  }

  login(username: string, password: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/auth/login`, { username, password }).pipe(
      tap(() => {
        sessionStorage.setItem(USER_KEY, username);
        sessionStorage.setItem(PASS_KEY, password);
      }),
    );
  }

  loadCurrentUser(): Observable<CurrentUser> {
    return this.http.get<CurrentUser>(`${environment.apiUrl}/auth/me`).pipe(
      tap((user) => this.currentUser.set(user)),
      catchError((err) => {
        this.clearSession();
        return throwError(() => err);
      }),
    );
  }

  refreshUserIfPossible(): void {
    if (!this.hasStoredCredentials()) {
      this.currentUser.set(null);
      return;
    }
    this.loadCurrentUser().subscribe({
      error: () => {},
    });
  }

  isAdmin(): boolean {
    const roles = this.currentUser()?.roles ?? [];
    return roles.some((r) => r === 'ROLE_ADMIN');
  }

  logout(): void {
    this.clearSession();
    void this.router.navigate(['/login']);
  }

  private clearSession(): void {
    sessionStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(PASS_KEY);
    this.currentUser.set(null);
  }
}

import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

interface TokenResponse {
  accessToken: string;
  refreshToken: string | null;
  expiresAtEpoch: number;
  role: string;
}

const KEY_ACCESS = 'fp.access';
const KEY_REFRESH = 'fp.refresh';
const KEY_EXP = 'fp.exp';
const KEY_ROLE = 'fp.role';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly token = signal<string | null>(localStorage.getItem(KEY_ACCESS));
  readonly isAuthenticated = computed(() => {
    if (this.token() === null) return false;
    const exp = Number(localStorage.getItem(KEY_EXP) ?? sessionStorage.getItem(KEY_EXP));
    return exp > 0 && Math.floor(Date.now() / 1000) < exp;
  });

  readonly role = signal<string | null>(localStorage.getItem(KEY_ROLE) ?? sessionStorage.getItem(KEY_ROLE));
  readonly isAdmin = computed(() => this.isAuthenticated() && this.role() === 'ADMIN');

  login(username: string, password: string, rememberMe: boolean): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${environment.api}/api/auth/login`, { username, password, rememberMe })
      .pipe(tap(t => this.setTokens(t, rememberMe)));
  }

  logout(redirect = '/map'): void {
    [KEY_ACCESS, KEY_REFRESH, KEY_EXP, KEY_ROLE].forEach(k => {
      localStorage.removeItem(k);
      sessionStorage.removeItem(k);
    });
    this.token.set(null);
    this.role.set(null);
    this.router.navigate([redirect]);
  }

  private setTokens(t: TokenResponse, rememberMe: boolean): void {
    const store = rememberMe ? localStorage : sessionStorage;
    store.setItem(KEY_ACCESS, t.accessToken);
    if (t.refreshToken) store.setItem(KEY_REFRESH, t.refreshToken);
    store.setItem(KEY_EXP, String(t.expiresAtEpoch));
    store.setItem(KEY_ROLE, t.role);
    this.token.set(t.accessToken);
    this.role.set(t.role);
  }
}

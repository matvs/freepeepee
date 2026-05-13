import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

interface TokenResponse {
  accessToken: string;
  refreshToken: string | null;
  expiresAtEpoch: number;
}

const KEY_ACCESS = 'fp.access';
const KEY_REFRESH = 'fp.refresh';
const KEY_EXP = 'fp.exp';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly token = signal<string | null>(localStorage.getItem(KEY_ACCESS));
  readonly isAuthenticated = computed(() => this.token() !== null);

  login(username: string, password: string, rememberMe: boolean): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${environment.api}/api/auth/login`, { username, password, rememberMe })
      .pipe(tap(t => this.setTokens(t, rememberMe)));
  }

  logout(): void {
    [KEY_ACCESS, KEY_REFRESH, KEY_EXP].forEach(k => {
      localStorage.removeItem(k);
      sessionStorage.removeItem(k);
    });
    this.token.set(null);
    this.router.navigate(['/login']);
  }

  private setTokens(t: TokenResponse, rememberMe: boolean): void {
    const store = rememberMe ? localStorage : sessionStorage;
    store.setItem(KEY_ACCESS, t.accessToken);
    if (t.refreshToken) store.setItem(KEY_REFRESH, t.refreshToken);
    store.setItem(KEY_EXP, String(t.expiresAtEpoch));
    this.token.set(t.accessToken);
  }
}

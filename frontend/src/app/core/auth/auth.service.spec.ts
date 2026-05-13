import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let svc: AuthService;
  let http: HttpTestingController;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerSpy }
      ]
    });
    localStorage.clear();
    sessionStorage.clear();
    svc = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('stores access token in localStorage on rememberMe=true', () => {
    svc.login('matvs', 'lap00p00', true).subscribe();
    const req = http.expectOne(r => r.url.endsWith('/api/auth/login'));
    req.flush({ accessToken: 'a.b.c', refreshToken: 'r.e.f', expiresAtEpoch: 9999999 });
    expect(localStorage.getItem('fp.access')).toBe('a.b.c');
    expect(svc.isAuthenticated()).toBeTrue();
  });

  it('stores in sessionStorage when rememberMe=false', () => {
    svc.login('matvs', 'lap00p00', false).subscribe();
    http.expectOne(r => r.url.endsWith('/api/auth/login'))
        .flush({ accessToken: 'a.b.c', refreshToken: null, expiresAtEpoch: 9999999 });
    expect(localStorage.getItem('fp.access')).toBeNull();
    expect(sessionStorage.getItem('fp.access')).toBe('a.b.c');
  });

  it('clears tokens and redirects on logout', () => {
    localStorage.setItem('fp.access', 'x');
    sessionStorage.setItem('fp.access', 'y');
    svc.logout();
    expect(localStorage.getItem('fp.access')).toBeNull();
    expect(sessionStorage.getItem('fp.access')).toBeNull();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
  });
});

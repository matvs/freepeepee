import { TestBed } from '@angular/core/testing';
import { UrlTree } from '@angular/router';
import { runInInjectionContext, Injector } from '@angular/core';
import { adminGuard } from './admin.guard';
import { AuthService } from '../auth/auth.service';

describe('adminGuard', () => {
  function run(isAdmin: boolean) {
    const auth = { isAdmin: () => isAdmin } as Partial<AuthService>;
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: auth }]
    });
    const injector = TestBed.inject(Injector);
    return runInInjectionContext(injector, () => adminGuard({} as any, {} as any));
  }

  it('allows admins', () => {
    expect(run(true)).toBeTrue();
  });

  it('redirects non-admins to /admin', () => {
    const result = run(false);
    expect(result instanceof UrlTree).toBeTrue();
    expect((result as UrlTree).toString()).toBe('/admin');
  });
});

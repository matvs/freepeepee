import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { ToiletsListComponent } from './toilets-list.component';
import { AuthService } from '../../core/auth/auth.service';

function setup(isAdmin: boolean) {
  TestBed.configureTestingModule({
    imports: [ToiletsListComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideNoopAnimations(),
      { provide: AuthService, useValue: { isAdmin: signal(isAdmin) } }
    ]
  });
  const fixture = TestBed.createComponent(ToiletsListComponent);
  const http = TestBed.inject(HttpTestingController);
  // First change detection triggers the constructor effect -> list() load.
  fixture.detectChanges();
  http.match(r => r.url.endsWith('/api/toilets')).forEach(r => r.flush([]));
  fixture.detectChanges();
  return { fixture, http };
}

describe('ToiletsListComponent control visibility', () => {
  it('hides the "new freepeepee" button for anonymous users', () => {
    const { fixture, http } = setup(false);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('new freepeepee');
    http.verify();
  });

  it('shows the "new freepeepee" button for admins', () => {
    const { fixture, http } = setup(true);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('new freepeepee');
    http.verify();
  });
});

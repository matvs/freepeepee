import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { AuditLogComponent } from './audit-log.component';

describe('AuditLogComponent', () => {
  it('renders rows returned by the API', () => {
    TestBed.configureTestingModule({
      imports: [AuditLogComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()]
    });
    const fixture = TestBed.createComponent(AuditLogComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(r => r.url.endsWith('/api/audit')).flush({
      content: [{
        occurredAt: '2026-06-19T10:00:00Z', actorName: 'admin', operation: 'CREATE',
        entityType: 'Toilet', entityId: 'abc', fieldName: null, oldValue: null,
        newValue: '{...}', actorIp: '127.0.0.1'
      }],
      number: 0, size: 50, totalElements: 1, last: true
    });
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('admin');
    expect(text).toContain('CREATE');
  });
});

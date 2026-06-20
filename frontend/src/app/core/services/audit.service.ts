import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AuditEntry {
  occurredAt: string;
  actorName: string;
  operation: string;
  entityType: string;
  entityId: string | null;
  fieldName: string | null;
  oldValue: string | null;
  newValue: string | null;
  actorIp: string | null;
}

export interface AuditPage {
  content: AuditEntry[];
  number: number;
  size: number;
  totalElements: number;
  last: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.api}/api/audit`;

  page(page = 0, size = 50): Observable<AuditPage> {
    return this.http.get<AuditPage>(this.base, { params: { page, size } });
  }
}

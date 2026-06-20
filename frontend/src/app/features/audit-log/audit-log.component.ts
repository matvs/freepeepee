import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuditEntry, AuditService } from '../../core/services/audit.service';

@Component({
  selector: 'fp-audit-log',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatIconModule],
  templateUrl: './audit-log.component.pug',
  styleUrl: './audit-log.component.scss'
})
export class AuditLogComponent {
  private readonly api = inject(AuditService);

  readonly cols = ['occurredAt', 'actorName', 'operation', 'entityType', 'fieldName', 'change'];
  readonly rows = signal<AuditEntry[]>([]);
  readonly last = signal(true);
  private page = 0;

  constructor() { this.load(0); }

  private load(page: number): void {
    this.api.page(page, 50).subscribe(p => {
      this.rows.update(cur => page === 0 ? p.content : [...cur, ...p.content]);
      this.page = p.number;
      this.last.set(p.last);
    });
  }

  loadMore(): void { if (!this.last()) this.load(this.page + 1); }
}

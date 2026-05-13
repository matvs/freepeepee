import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, switchMap, of } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { ToiletService } from '../../core/services/toilet.service';
import { Toilet } from '../../shared/models/toilet';
import { ToiletFormComponent } from '../toilet-form/toilet-form.component';

@Component({
  selector: 'fp-toilets-list',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule,
    MatTableModule, MatIconModule, MatButtonModule,
    MatFormFieldModule, MatInputModule, MatChipsModule,
    MatDialogModule, MatSnackBarModule, MatTooltipModule
  ],
  templateUrl: './toilets-list.component.pug',
  styleUrl: './toilets-list.component.scss'
})
export class ToiletsListComponent implements OnInit {
  private readonly api = inject(ToiletService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);

  readonly cols = ['name', 'address', 'pinCode', 'isWorking', 'toiletType', 'notes', 'actions'];
  readonly rows = signal<Toilet[]>([]);
  readonly searchCtrl = new FormControl('', { nonNullable: true });

  /** Reactive filtered list - server-side via /search when non-empty, else local. */
  readonly searchTerm = toSignal(
    this.searchCtrl.valueChanges.pipe(debounceTime(250)),
    { initialValue: '' }
  );

  ngOnInit(): void { this.reload(); }

  reload(): void {
    const q = this.searchTerm()?.trim();
    const obs = q ? this.api.search(q) : this.api.list();
    obs.subscribe(rows => this.rows.set(rows));
  }

  add(): void {
    this.dialog.open(ToiletFormComponent, { width: '520px', data: null })
      .afterClosed().subscribe(saved => { if (saved) this.reload(); });
  }

  edit(t: Toilet): void {
    this.dialog.open(ToiletFormComponent, { width: '520px', data: t })
      .afterClosed().subscribe(saved => { if (saved) this.reload(); });
  }

  remove(t: Toilet): void {
    if (!confirm(`Delete "${t.name}"? This cannot be undone.`)) return;
    this.api.delete(t.id).subscribe({
      next: () => { this.snack.open('Deleted', 'ok', { duration: 2000 }); this.reload(); },
      error: () => this.snack.open('Delete failed', 'ok', { duration: 3000 })
    });
  }
}

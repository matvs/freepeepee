import { Component, computed, inject, signal, effect } from '@angular/core';
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
import { debounceTime } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';
import { ToiletService } from '../../core/services/toilet.service';
import { AuthService } from '../../core/auth/auth.service';
import { Toilet } from '../../shared/models/toilet';
import { ToiletFormComponent } from '../toilet-form/toilet-form.component';
import {
  FilterDialogComponent, FilterDialogData, ToiletFilter,
  DEFAULT_FILTER, applyFilter, collectCities, activeFilterCount
} from '../filter-dialog/filter-dialog.component';

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
export class ToiletsListComponent {
  private readonly api = inject(ToiletService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  readonly isAdmin = inject(AuthService).isAdmin;

  readonly cols = ['name', 'address', 'pinCode', 'isWorking', 'toiletType', 'notes', 'actions'];
  readonly allRows = signal<Toilet[]>([]);
  readonly filter = signal<ToiletFilter>(DEFAULT_FILTER);
  readonly rows = computed(() => applyFilter(this.allRows(), this.filter()));
  readonly filterCount = computed(() => activeFilterCount(this.filter()));

  readonly searchCtrl = new FormControl('', { nonNullable: true });
  readonly searchTerm = toSignal(
    this.searchCtrl.valueChanges.pipe(debounceTime(250)),
    { initialValue: '' }
  );

  constructor() { effect(() => { this.reload(); }); }

  reload(): void {
    const q = this.searchTerm()?.trim();
    const obs = q ? this.api.search(q) : this.api.list();
    obs.subscribe(rows => this.allRows.set(rows));
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

  navigate(t: Toilet): void {
    window.open(`https://www.google.com/maps/dir/?api=1&destination=${t.lat},${t.lon}`, '_blank', 'noopener');
  }

  openFilter(): void {
    const data: FilterDialogData = {
      current: this.filter(),
      cities: collectCities(this.allRows())
    };
    this.dialog.open(FilterDialogComponent, { data, width: '380px' })
      .afterClosed().subscribe((result: ToiletFilter | null) => {
        if (result !== null) this.filter.set(result);
      });
  }

  findNearMe(): void {
    if (!navigator.geolocation) {
      this.snack.open('Geolocation not supported', 'ok', { duration: 3000 });
      return;
    }
    navigator.geolocation.getCurrentPosition(
      pos => {
        this.api.nearby(pos.coords.latitude, pos.coords.longitude, 2000)
          .subscribe(rows => this.allRows.set(rows));
      },
      err => this.snack.open(`Location denied: ${err.message}`, 'ok', { duration: 3500 }),
      { enableHighAccuracy: true, timeout: 8000 }
    );
  }
}

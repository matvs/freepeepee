import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { Toilet, ToiletType } from '../../shared/models/toilet';

export interface ToiletFilter {
  city: string | null;
  types: ToiletType[];
  status: 'all' | 'working' | 'broken';
}

export const DEFAULT_FILTER: ToiletFilter = { city: null, types: [], status: 'all' };

export const ALL_TYPES: ToiletType[] = ['MCDONALDS', 'GAS_STATION', 'PARK', 'CAFE', 'PUBLIC', 'TRAIN', 'OTHER'];

export function extractCity(address: string): string {
  const i = address.lastIndexOf(',');
  if (i < 0) return address.trim();
  return address.slice(i + 1).trim().replace(/^\d[\d\s]*/, '').trim();
}

export function collectCities(toilets: Toilet[]): string[] {
  return [...new Set(toilets.map(t => extractCity(t.address)).filter(Boolean))].sort();
}

export function applyFilter(toilets: Toilet[], f: ToiletFilter): Toilet[] {
  return toilets.filter(t => {
    if (f.city && extractCity(t.address) !== f.city) return false;
    if (f.types.length > 0 && !f.types.includes(t.toiletType)) return false;
    if (f.status === 'working' && !t.isWorking) return false;
    if (f.status === 'broken' && t.isWorking) return false;
    return true;
  });
}

export function activeFilterCount(f: ToiletFilter): number {
  return (f.city ? 1 : 0) + (f.types.length > 0 ? 1 : 0) + (f.status !== 'all' ? 1 : 0);
}

export interface FilterDialogData {
  current: ToiletFilter;
  cities: string[];
}

@Component({
  selector: 'fp-filter-dialog',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatDialogModule, MatButtonModule,
    MatButtonToggleModule, MatSelectModule, MatFormFieldModule
  ],
  templateUrl: './filter-dialog.component.pug',
  styleUrl: './filter-dialog.component.scss'
})
export class FilterDialogComponent {
  draft: ToiletFilter;
  readonly cities: string[];
  readonly allTypes = ALL_TYPES;

  constructor(
    private readonly ref: MatDialogRef<FilterDialogComponent>,
    @Inject(MAT_DIALOG_DATA) data: FilterDialogData
  ) {
    this.cities = data.cities;
    this.draft = { ...data.current, types: [...data.current.types] };
  }

  toggleType(t: ToiletType): void {
    const i = this.draft.types.indexOf(t);
    if (i >= 0) this.draft.types.splice(i, 1);
    else this.draft.types.push(t);
  }

  isOn(t: ToiletType): boolean { return this.draft.types.includes(t); }

  reset(): void { this.draft = { city: null, types: [], status: 'all' }; }

  apply(): void { this.ref.close({ ...this.draft, types: [...this.draft.types] }); }

  cancel(): void { this.ref.close(null); }
}

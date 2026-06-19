import { Component, Inject, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { debounceTime, switchMap, catchError, of } from 'rxjs';
import { ToiletService } from '../../core/services/toilet.service';
import { Toilet, ToiletType } from '../../shared/models/toilet';

const TYPES: ToiletType[] = ['MCDONALDS', 'GAS_STATION', 'PARK', 'CAFE', 'PUBLIC', 'TRAIN', 'OTHER'];

interface GeoResult { display_name: string; lat: string; lon: string; }

@Component({
  selector: 'fp-toilet-form',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatDialogModule,
    MatFormFieldModule, MatInputModule, MatSelectModule,
    MatSlideToggleModule, MatButtonModule, MatIconModule,
    MatSnackBarModule
  ],
  templateUrl: './toilet-form.component.pug',
  styleUrl: './toilet-form.component.scss'
})
export class ToiletFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ToiletService);
  private readonly http = inject(HttpClient);
  private readonly snack = inject(MatSnackBar);

  readonly types = TYPES;
  readonly submitting = signal(false);
  readonly isEdit: boolean;
  readonly geoResults = signal<GeoResult[]>([]);

  readonly form = this.fb.nonNullable.group({
    name:       ['', [Validators.required, Validators.maxLength(120)]],
    address:    ['', [Validators.required, Validators.maxLength(255)]],
    lat:        [47.3769, [Validators.required, Validators.min(-90),  Validators.max(90)]],
    lon:        [8.5417,  [Validators.required, Validators.min(-180), Validators.max(180)]],
    pinCode:    [null as string | null],
    isWorking:  [true],
    toiletType: ['OTHER' as ToiletType, Validators.required],
    notes:      [null as string | null]
  });

  constructor(
    private readonly ref: MatDialogRef<ToiletFormComponent>,
    @Inject(MAT_DIALOG_DATA) private readonly data: Toilet | null
  ) {
    this.isEdit = data !== null;
    if (data) {
      this.form.patchValue({
        name: data.name, address: data.address, lat: data.lat, lon: data.lon,
        pinCode: data.pinCode, isWorking: data.isWorking,
        toiletType: data.toiletType, notes: data.notes
      });
    }
  }

  ngOnInit(): void {
    this.form.get('address')!.valueChanges.pipe(
      debounceTime(350),
      switchMap(q => q && q.length > 3
        ? this.http.get<GeoResult[]>('https://nominatim.openstreetmap.org/search', {
            params: { q, format: 'json', limit: '6', addressdetails: '0' }
          }).pipe(catchError(() => of([])))
        : of([])
      )
    ).subscribe(r => this.geoResults.set(r));
  }

  pickGeoResult(r: GeoResult): void {
    this.form.patchValue({ address: r.display_name, lat: +r.lat, lon: +r.lon });
    this.geoResults.set([]);
  }

  coordsLabel(): string {
    const lat = this.form.get('lat')!.value;
    const lon = this.form.get('lon')!.value;
    if (lat === 47.3769 && lon === 8.5417 && !this.isEdit) return '';
    return `${(+lat).toFixed(5)}, ${(+lon).toFixed(5)}`;
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    const v = this.form.getRawValue();
    const obs = this.isEdit
      ? this.api.update(this.data!.id, { ...v, version: this.data!.version })
      : this.api.create(v);
    obs.subscribe({
      next: () => this.ref.close(true),
      error: e => {
        this.submitting.set(false);
        this.snack.open(e.status === 409 ? 'Version conflict – reload and retry.' : 'Save failed.', 'ok', { duration: 3500 });
      }
    });
  }

  cancel(): void { this.ref.close(false); }
}

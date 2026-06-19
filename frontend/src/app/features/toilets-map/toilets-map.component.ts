import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import * as L from 'leaflet';
import { ToiletService } from '../../core/services/toilet.service';
import { Toilet } from '../../shared/models/toilet';
import { ToiletFormComponent } from '../toilet-form/toilet-form.component';
import {
  FilterDialogComponent, FilterDialogData, ToiletFilter,
  DEFAULT_FILTER, applyFilter, collectCities, activeFilterCount
} from '../filter-dialog/filter-dialog.component';

const ZURICH: [number, number] = [47.3769, 8.5417];

const TOILET_ICON_SVG = `
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 80" width="36" height="44">
  <defs>
    <filter id="s" x="-30%" y="-10%" width="160%" height="140%">
      <feDropShadow dx="0" dy="2" stdDeviation="2" flood-opacity=".5"/>
    </filter>
  </defs>
  <g filter="url(#s)">
    <path d="M32 78 C 18 60 4 50 4 28 a28 28 0 1 1 56 0 c 0 22 -14 32 -28 50z" fill="#9e6c0a" stroke="#1a1410" stroke-width="2"/>
    <circle cx="32" cy="28" r="18" fill="#f2e8d2"/>
    <rect x="22" y="18" width="20" height="14" rx="2" fill="#1a1410"/>
    <rect x="20" y="32" width="24" height="3" fill="#1a1410"/>
    <rect x="26" y="35" width="12" height="6" fill="#1a1410"/>
  </g>
</svg>`;

@Component({
  selector: 'fp-toilets-map',
  standalone: true,
  imports: [
    CommonModule, MatButtonModule, MatIconModule,
    MatTooltipModule, MatSnackBarModule, MatDialogModule
  ],
  templateUrl: './toilets-map.component.pug',
  styleUrl: './toilets-map.component.scss'
})
export class ToiletsMapComponent implements AfterViewInit, OnDestroy {
  @ViewChild('mapEl', { static: true }) mapEl!: ElementRef<HTMLDivElement>;

  private readonly api = inject(ToiletService);
  private readonly snack = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  private map?: L.Map;
  private layer?: L.LayerGroup;
  private allRows: Toilet[] = [];
  private renderedRows: Toilet[] = [];

  readonly count = signal(0);
  readonly filter = signal<ToiletFilter>(DEFAULT_FILTER);
  readonly filterCount = computed(() => activeFilterCount(this.filter()));

  ngAfterViewInit(): void {
    this.map = L.map(this.mapEl.nativeElement, { zoomControl: true }).setView(ZURICH, 14);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© OpenStreetMap contributors'
    }).addTo(this.map);
    this.layer = L.layerGroup().addTo(this.map);
    this.map.on('moveend zoomend', () => this.updateVisibleCount());
    this.loadAll();
  }

  ngOnDestroy(): void { this.map?.remove(); }

  loadAll(): void {
    this.api.list().subscribe(rows => {
      this.allRows = rows;
      this.renderFiltered(true);
    });
  }

  findNearMe(): void {
    if (!navigator.geolocation) {
      this.snack.open('Geolocation not supported by this browser.', 'ok', { duration: 3000 });
      return;
    }
    navigator.geolocation.getCurrentPosition(
      pos => {
        const { latitude: lat, longitude: lon } = pos.coords;
        this.map?.flyTo([lat, lon], 15);
        L.circle([lat, lon], { radius: 30, color: '#fcd62c' }).addTo(this.layer!);
        this.api.nearby(lat, lon, 2000).subscribe(rows => {
          this.allRows = rows;
          this.renderFiltered(false);
        });
      },
      err => this.snack.open(`Location denied: ${err.message}`, 'ok', { duration: 3500 }),
      { enableHighAccuracy: true, timeout: 8000 }
    );
  }

  add(): void {
    this.dialog.open(ToiletFormComponent, { width: '520px', data: null })
      .afterClosed().subscribe(saved => { if (saved) this.loadAll(); });
  }

  openFilter(): void {
    const data: FilterDialogData = {
      current: this.filter(),
      cities: collectCities(this.allRows)
    };
    this.dialog.open(FilterDialogComponent, { data, width: '380px' })
      .afterClosed().subscribe((result: ToiletFilter | null) => {
        if (result !== null) {
          this.filter.set(result);
          this.renderFiltered(true);
        }
      });
  }

  private renderFiltered(autoZoom: boolean): void {
    const filtered = applyFilter(this.allRows, this.filter());
    this.renderedRows = filtered;
    this.layer?.clearLayers();
    filtered.forEach(t => this.addMarker(t));
    if (autoZoom && filtered.length > 0) {
      const bounds = L.latLngBounds(filtered.map(t => [t.lat, t.lon] as [number, number]));
      this.map?.fitBounds(bounds, { padding: [48, 48], maxZoom: 16 });
    }
    this.updateVisibleCount();
  }

  private updateVisibleCount(): void {
    const bounds = this.map?.getBounds();
    if (!bounds) { this.count.set(this.renderedRows.length); return; }
    this.count.set(this.renderedRows.filter(t => bounds.contains([t.lat, t.lon])).length);
  }

  private addMarker(t: Toilet): void {
    const icon = L.divIcon({
      className: 'fp-pin',
      html: TOILET_ICON_SVG,
      iconSize: [36, 44],
      iconAnchor: [18, 44],
      popupAnchor: [0, -40]
    });
    const dotClass = t.isWorking ? 'fp-dot--ok' : 'fp-dot--bad';
    const mapsUrl = `https://www.google.com/maps/dir/?api=1&destination=${t.lat},${t.lon}`;
    const popup = `
      <div class="fp-popup">
        <div class="fp-popup__title">${esc(t.name)}</div>
        <div class="fp-popup__addr">${esc(t.address)}</div>
        <div class="fp-popup__status"><span class="fp-dot ${dotClass}"></span></div>
        <div class="fp-popup__row"><b>type:</b>&nbsp;${t.toiletType}</div>
        ${t.pinCode ? `<div class="fp-popup__row"><b>pin:</b>&nbsp;<code>${esc(t.pinCode)}</code></div>` : ''}
        ${t.notes ? `<div class="fp-popup__notes">${esc(t.notes)}</div>` : ''}
        <a href="${mapsUrl}" target="_blank" rel="noopener" class="fp-popup__nav">directions ↗</a>
      </div>`;
    L.marker([t.lat, t.lon], { icon }).bindPopup(popup).addTo(this.layer!);
  }
}

function esc(s: string | null | undefined): string {
  if (!s) return '';
  return s.replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c] as string));
}

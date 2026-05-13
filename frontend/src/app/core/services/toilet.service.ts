import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Toilet, ToiletCreate, ToiletUpdate } from '../../shared/models/toilet';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ToiletService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.api}/api/toilets`;

  list(): Observable<Toilet[]> { return this.http.get<Toilet[]>(this.base); }
  get(id: string): Observable<Toilet> { return this.http.get<Toilet>(`${this.base}/${id}`); }
  create(t: ToiletCreate): Observable<Toilet> { return this.http.post<Toilet>(this.base, t); }
  update(id: string, t: ToiletUpdate): Observable<Toilet> { return this.http.put<Toilet>(`${this.base}/${id}`, t); }
  delete(id: string): Observable<void> { return this.http.delete<void>(`${this.base}/${id}`); }
  nearby(lat: number, lon: number, radius = 1500): Observable<Toilet[]> {
    return this.http.get<Toilet[]>(`${this.base}/nearby`, { params: { lat, lon, radius } });
  }
  search(q: string): Observable<Toilet[]> {
    return this.http.get<Toilet[]>(`${this.base}/search`, { params: { q } });
  }
}

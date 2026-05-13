export type ToiletType = 'MCDONALDS' | 'GAS_STATION' | 'PARK' | 'CAFE' | 'PUBLIC' | 'OTHER';

export interface Toilet {
  id: string;
  name: string;
  address: string;
  lat: number;
  lon: number;
  pinCode: string | null;
  isWorking: boolean;
  toiletType: ToiletType;
  notes: string | null;
  version: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface ToiletCreate {
  name: string; address: string; lat: number; lon: number;
  pinCode: string | null; isWorking: boolean; toiletType: ToiletType; notes: string | null;
}

export interface ToiletUpdate extends ToiletCreate { version: number; }

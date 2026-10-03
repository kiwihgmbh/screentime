import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  Account,
  Adjustment,
  ApiError,
  CheckResult,
  Device,
  Session,
  SessionType,
  Settings,
  User,
  Week,
  WeekFlags,
  WeekSummary,
} from './models';

/** Every call the screens make. One place, so a changed path is a changed line. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // ----------------------------------------------------------------- reading

  account(): Promise<Account> {
    return firstValueFrom(this.http.get<Account>('/api/account/current'));
  }

  week(start?: string): Promise<Week> {
    const params = start ? new HttpParams().set('start', start) : undefined;
    return firstValueFrom(this.http.get<Week>('/api/account/week', { params }));
  }

  history(weeks = 12): Promise<WeekSummary[]> {
    return firstValueFrom(
      this.http.get<WeekSummary[]>('/api/account/history', {
        params: new HttpParams().set('weeks', weeks),
      }),
    );
  }

  sessions(from: string, to: string): Promise<Session[]> {
    return firstValueFrom(
      this.http.get<Session[]>('/api/sessions', {
        params: new HttpParams().set('from', from).set('to', to),
      }),
    );
  }

  devices(): Promise<Device[]> {
    return firstValueFrom(this.http.get<Device[]>('/api/sessions/devices'));
  }

  // -------------------------------------------------------------- the timer

  start(deviceId: number, type: SessionType): Promise<Session> {
    return firstValueFrom(this.http.post<Session>('/api/sessions/start', { deviceId, type }));
  }

  stop(): Promise<Session> {
    return firstValueFrom(this.http.post<Session>('/api/sessions/stop', {}));
  }

  /**
   * The date is only ever sent by a parent correcting another day. For a child
   * the server checks it against its own today and refuses anything else.
   */
  bookManually(entry: {
    minutes: number;
    deviceId: number;
    type: SessionType;
    date?: string;
    note?: string;
  }): Promise<Session> {
    return firstValueFrom(this.http.post<Session>('/api/sessions/manual', entry));
  }

  updateSession(
    id: number,
    change: {
      minutes?: number;
      deviceId?: number;
      type?: SessionType;
      date?: string;
      note?: string;
    },
  ): Promise<Session> {
    return firstValueFrom(this.http.put<Session>(`/api/sessions/${id}`, change));
  }

  deleteSession(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/sessions/${id}`));
  }

  // --------------------------------------------------------- parent screens

  saveCheck(
    weekStart: string,
    reported: { deviceId: number; minutes: number }[],
    deliberate: boolean,
  ): Promise<CheckResult> {
    return firstValueFrom(
      this.http.post<CheckResult>('/api/checks', { weekStart, reported, deliberate }),
    );
  }

  addAdjustment(weekStart: string, minutes: number, reason: string): Promise<Adjustment> {
    return firstValueFrom(
      this.http.post<Adjustment>('/api/adjustments', { weekStart, minutes, reason }),
    );
  }

  deleteAdjustment(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/adjustments/${id}`));
  }

  settings(): Promise<Settings> {
    return firstValueFrom(this.http.get<Settings>('/api/settings'));
  }

  saveSettings(changes: Settings): Promise<Settings> {
    return firstValueFrom(this.http.put<Settings>('/api/settings', changes));
  }

  weekFlags(weekStart: string): Promise<WeekFlags> {
    return firstValueFrom(this.http.get<WeekFlags>(`/api/weeks/${weekStart}`));
  }

  setHoliday(weekStart: string, holiday: boolean): Promise<WeekFlags> {
    return firstValueFrom(this.http.put<WeekFlags>(`/api/weeks/${weekStart}/holiday`, { holiday }));
  }

  users(): Promise<User[]> {
    return firstValueFrom(this.http.get<User[]>('/api/users'));
  }

  createUser(user: {
    username: string;
    password: string;
    displayName?: string;
    role: string;
  }): Promise<User> {
    return firstValueFrom(this.http.post<User>('/api/users', user));
  }

  updateUser(
    id: number,
    change: { displayName?: string; password?: string; role?: string; active?: boolean },
  ): Promise<User> {
    return firstValueFrom(this.http.put<User>(`/api/users/${id}`, change));
  }
}

/**
 * The message the server sent, or a readable fallback. The backend writes its
 * messages to be shown to a child, so they are shown as they are.
 */
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const body = error.error as ApiError | string | null;
    if (body && typeof body === 'object' && typeof body.message === 'string') {
      return body.message;
    }
    if (error.status === 0) {
      return 'The server cannot be reached.';
    }
  }
  return 'Something went wrong.';
}

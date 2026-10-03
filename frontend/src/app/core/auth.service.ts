import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { LoginResult, Role } from './models';

const STORAGE_KEY = 'screentime.session';

interface StoredSession {
  token: string;
  expiresAt: string;
  userId: number;
  username: string;
  displayName: string;
  role: Role;
}

/**
 * Who is signed in. The token lasts thirty days on purpose: a child who is
 * logged out every day stops booking, and an account nobody books in is worse
 * than no account.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly session = signal<StoredSession | null>(restore());

  readonly user = computed(() => this.session());
  readonly signedIn = computed(() => this.session() !== null);
  readonly isParent = computed(() => this.session()?.role === 'PARENT');
  readonly displayName = computed(() => this.session()?.displayName ?? '');

  token(): string | null {
    return this.session()?.token ?? null;
  }

  async login(username: string, password: string): Promise<void> {
    const result = await firstValueFrom(
      this.http.post<LoginResult>('/api/auth/login', { username, password }),
    );
    const stored: StoredSession = {
      token: result.token,
      expiresAt: result.expiresAt,
      userId: result.userId,
      username: result.username,
      displayName: result.displayName,
      role: result.role,
    };
    this.session.set(stored);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
    } catch {
      // a browser with storage switched off still works for this session
    }
  }

  logout(): void {
    this.session.set(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // nothing to clean up
    }
    void this.router.navigate(['/login']);
  }
}

function restore(): StoredSession | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const stored = JSON.parse(raw) as StoredSession;
    // an expired token is the same as no token, and finding that out here
    // saves the child a failed request and a confusing error
    if (!stored.token || new Date(stored.expiresAt).getTime() <= Date.now()) {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return stored;
  } catch {
    return null;
  }
}

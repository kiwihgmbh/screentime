import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from './auth.service';

/** logout() navigates to /login, so that route has to exist here. */
const ROUTES = [{ path: 'login', children: [] }];

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(ROUTES)],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  it('starts signed out', () => {
    expect(auth.signedIn()).toBe(false);
    expect(auth.isParent()).toBe(false);
    expect(auth.token()).toBeNull();
  });

  it('keeps the role so the menu can hide the parent screens', async () => {
    const login = auth.login('parent', 'a-password');
    http.expectOne('/api/auth/login').flush(result('PARENT'));
    await login;

    expect(auth.signedIn()).toBe(true);
    expect(auth.isParent()).toBe(true);
    expect(auth.displayName()).toBe('A Parent');
    expect(auth.token()).toBe('a-token');
  });

  it('does not treat a child as a parent', async () => {
    const login = auth.login('child', 'a-password');
    http.expectOne('/api/auth/login').flush(result('CHILD'));
    await login;

    expect(auth.signedIn()).toBe(true);
    expect(auth.isParent()).toBe(false);
  });

  it('survives a reload, because a child logged out daily stops booking', async () => {
    const login = auth.login('child', 'a-password');
    http.expectOne('/api/auth/login').flush(result('CHILD'));
    await login;

    // a reload is a fresh injector reading what the last one stored
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(ROUTES)],
    });
    const restored = TestBed.inject(AuthService);

    expect(restored.signedIn()).toBe(true);
    expect(restored.token()).toBe('a-token');
    expect(restored.displayName()).toBe('A Child');
  });

  it('ignores a stored session whose token has already expired', () => {
    localStorage.setItem(
      'screentime.session',
      JSON.stringify({ ...result('CHILD'), expiresAt: new Date(Date.now() - 1000).toISOString() }),
    );
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(ROUTES)],
    });
    expect(TestBed.inject(AuthService).signedIn()).toBe(false);
  });

  it('forgets everything on sign out', async () => {
    const login = auth.login('parent', 'a-password');
    http.expectOne('/api/auth/login').flush(result('PARENT'));
    await login;

    auth.logout();

    expect(auth.signedIn()).toBe(false);
    expect(localStorage.getItem('screentime.session')).toBeNull();
  });

  function result(role: 'CHILD' | 'PARENT') {
    return {
      token: 'a-token',
      expiresAt: new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString(),
      userId: 1,
      username: role === 'PARENT' ? 'parent' : 'child',
      displayName: role === 'PARENT' ? 'A Parent' : 'A Child',
      role,
    };
  }
});

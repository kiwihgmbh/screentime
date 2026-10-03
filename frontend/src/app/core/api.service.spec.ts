import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { ApiService, errorMessage } from './api.service';

/**
 * The server writes its refusals to be read by a child. If the frontend
 * swallows them and shows "Something went wrong" instead, the app stops being
 * able to explain itself, which is the one thing it has to do.
 */
describe('errorMessage', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  /** Fails a request the way the server would, and returns what the UI would show. */
  async function messageFor(status: number, body: unknown, contentType = 'application/json') {
    const pending = api.account().catch((error: unknown) => errorMessage(error));
    http.expectOne('/api/account/current').flush(body as object, {
      status,
      statusText: 'error',
      headers: { 'Content-Type': contentType },
    });
    return pending;
  }

  it('shows the message the server sent, not a generic one', async () => {
    // exactly what GET /api/account/current returns for a parent before the
    // child account exists
    const message = await messageFor(409, {
      status: 409,
      error: 'Conflict',
      message: 'There is no child account yet. Create one under users first.',
      at: '2026-10-03T12:48:51Z',
    });

    expect(message).toBe('There is no child account yet. Create one under users first.');
  });

  it('shows the reason a rule refused something', async () => {
    const message = await messageFor(403, {
      status: 403,
      error: 'Forbidden',
      message: 'You can only book time for today. Ask a parent to correct another day.',
      at: '2026-10-03T12:48:51Z',
    });

    expect(message).toContain('only book time for today');
  });

  it('says the server cannot be reached when it cannot', async () => {
    const pending = api.account().catch((error: unknown) => errorMessage(error));
    http.expectOne('/api/account/current').error(new ProgressEvent('network'), { status: 0 });
    expect(await pending).toBe('The server cannot be reached.');
  });

  it('falls back only when there is really nothing to show', async () => {
    expect(await messageFor(500, '<html>gateway error</html>', 'text/html'))
      .toBe('Something went wrong.');
  });
});

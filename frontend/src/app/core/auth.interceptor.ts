import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Adds the bearer token, and signs the user out when the server says the token
 * is no longer good. Everything else is left to the caller to report.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const token = auth.token();

  const withToken =
    token && request.url.startsWith('/api') && !request.url.endsWith('/auth/login')
      ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : request;

  return next(withToken).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && auth.signedIn()) {
        auth.logout();
      }
      return throwError(() => error);
    }),
  );
};

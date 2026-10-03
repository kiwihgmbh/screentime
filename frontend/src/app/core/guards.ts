import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const signedIn: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.signedIn()) {
    return true;
  }
  return inject(Router).createUrlTree(['/login']);
};

/** The parent screens. The API enforces this too; this only keeps the menu honest. */
export const parentOnly: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.isParent()) {
    return true;
  }
  return inject(Router).createUrlTree(['/']);
};

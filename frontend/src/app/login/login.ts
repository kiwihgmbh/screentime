import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { errorMessage } from '../core/api.service';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, MatCardModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  styles: `
    :host {
      display: grid;
      place-items: center;
      min-height: 70vh;
    }
    mat-card {
      width: min(100%, 360px);
    }
    .wide {
      width: 100%;
    }
    h1 {
      font-size: 1.5rem;
      font-weight: 400;
      margin: 0 0 4px;
    }
    .sub {
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.9rem;
      margin: 0 0 16px;
    }
    .error {
      color: var(--mat-sys-error);
    }
  `,
  template: `
    <mat-card
      ><mat-card-content>
        <h1 i18n>Screentime</h1>
        <p class="sub" i18n>Sign in to see your account.</p>

        <form (ngSubmit)="submit()">
          <mat-form-field appearance="outline" class="wide">
            <mat-label i18n>User name</mat-label>
            <input
              matInput
              name="username"
              autocomplete="username"
              [(ngModel)]="username"
              [disabled]="busy()"
            />
          </mat-form-field>
          <mat-form-field appearance="outline" class="wide">
            <mat-label i18n>Password</mat-label>
            <input
              matInput
              type="password"
              name="password"
              autocomplete="current-password"
              [(ngModel)]="password"
              [disabled]="busy()"
            />
          </mat-form-field>

          @if (failure(); as message) {
            <p class="error">{{ message }}</p>
          }

          <button
            matButton="filled"
            class="wide"
            type="submit"
            [disabled]="busy() || !username() || !password()"
          >
            <span i18n>Sign in</span>
          </button>
        </form>
      </mat-card-content></mat-card
    >
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly username = signal('');
  protected readonly password = signal('');
  protected readonly busy = signal(false);
  protected readonly failure = signal<string | null>(null);

  protected async submit(): Promise<void> {
    this.busy.set(true);
    this.failure.set(null);
    try {
      await this.auth.login(this.username().trim(), this.password());
      await this.router.navigate(['/']);
    } catch (error) {
      this.failure.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}

import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService, errorMessage } from '../../core/api.service';
import { Role, User } from '../../core/models';

/** Accounts. There is no registration: a parent creates them here. */
@Component({
  selector: 'app-users',
  imports: [
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
  ],
  styles: `
    .stack {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }
    .wide {
      width: 100%;
    }
    .person {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .person .who {
      flex: 1 1 auto;
    }
    .person .name {
      font-weight: 500;
    }
    .person .meta {
      font-size: 0.78rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .error {
      color: var(--mat-sys-error);
    }
  `,
  template: `
    <h2 i18n>Accounts</h2>
    <div class="stack">
      @for (user of users(); track user.id) {
        <mat-card
          ><mat-card-content class="person">
            <mat-icon>{{ user.role === 'PARENT' ? 'shield_person' : 'face' }}</mat-icon>
            <div class="who">
              <div class="name">{{ user.displayName }}</div>
              <div class="meta">{{ user.username }} · {{ user.role }}</div>
            </div>
            <mat-slide-toggle
              [ngModel]="user.active"
              (ngModelChange)="setActive(user, $event)"
              [name]="'active-' + user.id"
            >
              <span i18n>Active</span>
            </mat-slide-toggle>
            <button
              matIconButton
              (click)="beginPassword(user)"
              [attr.aria-label]="'Change the password'"
              i18n-aria-label
            >
              <mat-icon>key</mat-icon>
            </button>
          </mat-card-content></mat-card
        >
      }

      @if (passwordFor(); as user) {
        <mat-card
          ><mat-card-content>
            <h3 i18n>New password for {{ user.displayName }}</h3>
            <mat-form-field appearance="outline" class="wide">
              <mat-label i18n>Password</mat-label>
              <input
                matInput
                type="password"
                autocomplete="new-password"
                [(ngModel)]="newPassword"
                name="newPassword"
              />
            </mat-form-field>
            <div class="row">
              <button
                matButton="filled"
                (click)="savePassword()"
                [disabled]="busy() || newPassword().length < 8"
                i18n
              >
                Save
              </button>
              <button matButton (click)="passwordFor.set(null)" i18n>Cancel</button>
            </div>
          </mat-card-content></mat-card
        >
      }

      <mat-card
        ><mat-card-content>
          <h3 i18n>Add an account</h3>
          <div class="row">
            <mat-form-field appearance="outline">
              <mat-label i18n>User name</mat-label>
              <input matInput [(ngModel)]="username" name="username" autocomplete="off" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label i18n>Name shown</mat-label>
              <input matInput [(ngModel)]="displayName" name="displayName" />
            </mat-form-field>
          </div>
          <div class="row">
            <mat-form-field appearance="outline">
              <mat-label i18n>Password</mat-label>
              <input
                matInput
                type="password"
                autocomplete="new-password"
                [(ngModel)]="password"
                name="password"
              />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label i18n>Role</mat-label>
              <mat-select [(ngModel)]="role" name="role">
                <mat-option value="CHILD" i18n>Child</mat-option>
                <mat-option value="PARENT" i18n>Parent</mat-option>
              </mat-select>
            </mat-form-field>
          </div>
          @if (failure(); as message) {
            <p class="error">{{ message }}</p>
          }
          <button
            matButton="filled"
            class="wide"
            (click)="create()"
            [disabled]="busy() || !username() || password().length < 8"
          >
            <mat-icon>person_add</mat-icon>
            <span i18n>Create</span>
          </button>
        </mat-card-content></mat-card
      >
    </div>
  `,
})
export class UsersComponent {
  private readonly api = inject(ApiService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly users = signal<User[]>([]);
  protected readonly busy = signal(false);
  protected readonly failure = signal<string | null>(null);

  protected readonly username = signal('');
  protected readonly displayName = signal('');
  protected readonly password = signal('');
  protected readonly role = signal<Role>('CHILD');

  protected readonly passwordFor = signal<User | null>(null);
  protected readonly newPassword = signal('');

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      this.users.set(await this.api.users());
    } catch (error) {
      this.failure.set(errorMessage(error));
    }
  }

  protected async create(): Promise<void> {
    this.busy.set(true);
    this.failure.set(null);
    try {
      await this.api.createUser({
        username: this.username().trim(),
        password: this.password(),
        displayName: this.displayName().trim() || undefined,
        role: this.role(),
      });
      this.username.set('');
      this.displayName.set('');
      this.password.set('');
      await this.load();
    } catch (error) {
      this.failure.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }

  protected beginPassword(user: User): void {
    this.passwordFor.set(user);
    this.newPassword.set('');
  }

  protected async savePassword(): Promise<void> {
    const user = this.passwordFor();
    if (!user) {
      return;
    }
    await this.run(async () => {
      await this.api.updateUser(user.id, { password: this.newPassword() });
      this.passwordFor.set(null);
      this.newPassword.set('');
    });
  }

  protected async setActive(user: User, active: boolean): Promise<void> {
    await this.run(() => this.api.updateUser(user.id, { active }));
  }

  private async run(action: () => Promise<unknown>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
      await this.load();
    } catch (error) {
      this.snackBar.open(errorMessage(error), undefined, { duration: 6000 });
      await this.load();
    } finally {
      this.busy.set(false);
    }
  }
}

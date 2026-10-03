import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService, errorMessage } from '../../core/api.service';
import { SETTING_KEYS, Settings } from '../../core/models';

/**
 * Every number the rules run on. They are rows in a table, so changing one
 * here changes the account on the next request, with no deployment.
 *
 * The server validates the whole set together and refuses a change that would
 * leave the daily ceilings at or below the weekly budget; its message names
 * both numbers and is shown here as it is.
 */
@Component({
  selector: 'app-settings',
  imports: [
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
  ],
  styles: `
    .grid {
      display: grid;
      gap: 4px;
    }
    .field {
      display: grid;
      grid-template-columns: 1fr 110px;
      gap: 12px;
      align-items: center;
    }
    .label .name {
      font-weight: 500;
    }
    .label .hint {
      font-size: 0.78rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .wide {
      width: 100%;
    }
    .error {
      color: var(--mat-sys-error);
    }
    .aside {
      font-size: 0.85rem;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
  template: `
    <h2 i18n>Settings</h2>
    <mat-card
      ><mat-card-content>
        <div class="grid">
          @for (key of keys; track key) {
            <div class="field">
              <div class="label">
                <div class="name">{{ label(key) }}</div>
                <div class="hint">{{ hint(key) }}</div>
              </div>
              <mat-form-field appearance="outline">
                <input
                  matInput
                  type="number"
                  [ngModel]="draft()[key] ?? ''"
                  (ngModelChange)="set(key, $event)"
                  [name]="key"
                />
              </mat-form-field>
            </div>
          }
        </div>

        @if (failure(); as message) {
          <p class="error">{{ message }}</p>
        }
        <p class="aside" i18n>
          The daily ceilings have to add up to more than the weekly budget. If they do not, the
          weekly budget never binds and only the daily ceilings do any work.
        </p>
        <button matButton="filled" class="wide" (click)="save()" [disabled]="busy()">
          <mat-icon>save</mat-icon>
          <span i18n>Save</span>
        </button>
      </mat-card-content></mat-card
    >
  `,
})
export class SettingsComponent {
  private readonly api = inject(ApiService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly keys = SETTING_KEYS;
  protected readonly draft = signal<Settings>({});
  protected readonly busy = signal(false);
  protected readonly failure = signal<string | null>(null);

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      this.draft.set({ ...(await this.api.settings()) });
    } catch (error) {
      this.failure.set(errorMessage(error));
    }
  }

  protected set(key: string, value: string): void {
    this.draft.update((current) => ({ ...current, [key]: String(value) }));
  }

  protected async save(): Promise<void> {
    this.busy.set(true);
    this.failure.set(null);
    try {
      this.draft.set({ ...(await this.api.saveSettings(this.draft())) });
      this.snackBar.open($localize`Saved.`, undefined, { duration: 3000 });
    } catch (error) {
      // the server's message names the numbers that do not add up
      this.failure.set(errorMessage(error));
      await this.load();
    } finally {
      this.busy.set(false);
    }
  }

  protected label(key: string): string {
    return LABELS[key]?.name ?? key;
  }

  protected hint(key: string): string {
    return LABELS[key]?.hint ?? '';
  }
}

const LABELS: Record<string, { name: string; hint: string }> = {
  weeklyMinutes: { name: $localize`Weekly budget`, hint: $localize`Minutes, Monday to Sunday` },
  weekdayCapMinutes: { name: $localize`School day ceiling`, hint: $localize`Monday to Friday` },
  weekendCapMinutes: { name: $localize`Weekend ceiling`, hint: $localize`Saturday and Sunday` },
  quickDailyMinutes: {
    name: $localize`Looking things up`,
    hint: $localize`Per day, outside the weekly budget`,
  },
  cutoffHour: {
    name: $localize`Evening cut off`,
    hint: $localize`Nothing starts at or after this hour`,
  },
  bonusMinutes: { name: $localize`Bonus`, hint: $localize`Added to the week after a clean week` },
  bonusWeekendCapMinutes: {
    name: $localize`Weekend ceiling with bonus`,
    hint: $localize`So the extra time can be used`,
  },
  maxPenaltyMinutes: {
    name: $localize`Largest deduction`,
    hint: $localize`What one check can cost at most`,
  },
  toleranceMinutes: {
    name: $localize`Tolerance`,
    hint: $localize`A difference this small still counts as a match`,
  },
  manualMaxMinutes: {
    name: $localize`Largest single entry`,
    hint: $localize`What a child may book at once`,
  },
  deliberatePenaltyMinutes: {
    name: $localize`Working around the rules`,
    hint: $localize`Added before the cap`,
  },
};

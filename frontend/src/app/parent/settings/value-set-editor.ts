import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService, errorMessage } from '../../core/api.service';
import { Settings, SettingsOverview, SettingsScope } from '../../core/models';
import {
  RULE_TEXT,
  SETTING_LABELS,
  SettingsProblem,
  THRESHOLD_KEY,
  validateThreshold,
  validateValues,
} from '../../core/settings-rules';

type ApplyFrom = 'THIS' | 'NEXT';

/**
 * One value set: the term values, the holiday values, or the threshold.
 *
 * Every field shows the value in force, the rule it has to keep, and the
 * message when the rule is broken. The rules are checked as the numbers are
 * typed, with the same arithmetic the server uses, and Save stays disabled
 * while any of them is broken. The server still has the last word: it also
 * checks the weeks after the first one a change reaches, and its answer is
 * shown under the fields it names.
 */
@Component({
  selector: 'app-value-set-editor',
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatRadioModule,
  ],
  styles: `
    .field {
      display: grid;
      grid-template-columns: minmax(0, 1fr) 120px;
      gap: 4px 12px;
      align-items: start;
      padding: 6px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .field:last-of-type {
      border-bottom: none;
    }
    .name {
      font-weight: 500;
    }
    .rule,
    .pending {
      font-size: 0.78rem;
      margin-top: 2px;
      color: var(--mat-sys-on-surface-variant);
    }
    .pending {
      color: var(--mat-sys-tertiary);
    }
    .now {
      font-size: 0.78rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .error {
      grid-column: 1 / -1;
      font-size: 0.8rem;
      color: var(--mat-sys-error);
    }
    mat-form-field {
      margin-bottom: -1.25em;
    }
    .apply {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin: 12px 0;
    }
    .apply .detail {
      display: block;
      font-size: 0.8rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .wide {
      width: 100%;
    }
    .failure {
      color: var(--mat-sys-error);
    }
  `,
  template: `
    @for (key of keys(); track key) {
      <div class="field" [attr.data-key]="key">
        <!-- the name and the rule beside the input, so neither runs under it on a phone -->
        <div>
          <div class="name">{{ label(key) }}</div>
          <div class="rule">{{ rule(key) }}</div>
          @if (pending(key); as next) {
            <div class="pending" i18n>From {{ nextLabel() }}: {{ next }}, already saved.</div>
          }
          @if (isChanged(key)) {
            <div class="now" i18n>now {{ base()[key] }}</div>
          }
        </div>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <input
            matInput
            type="number"
            inputmode="numeric"
            [ngModel]="values()[key] ?? ''"
            (ngModelChange)="edit(key, $event)"
            [name]="scope() + '-' + key"
            [attr.aria-invalid]="errorsFor(key).length > 0"
          />
          <span matTextSuffix>{{ unit(key) }}</span>
        </mat-form-field>
        @for (message of errorsFor(key); track message) {
          <div class="error">{{ message }}</div>
        }
      </div>
    }

    <mat-radio-group
      class="apply"
      [ngModel]="applyFrom()"
      (ngModelChange)="applyFrom.set($event)"
      [name]="scope() + '-apply'"
      [attr.aria-label]="'When the change applies'"
      i18n-aria-label
    >
      <mat-radio-button value="THIS">
        <span i18n>From this week, {{ thisLabel() }}</span>
        <span class="detail" i18n>
          Changes this week's numbers from Monday on, including time already used. Last week and
          checks already made stay as they were.
        </span>
      </mat-radio-button>
      <mat-radio-button value="NEXT">
        <span i18n>From next week, {{ nextLabel() }}</span>
        <span class="detail" i18n>This week stays as it is. The new values start on Monday.</span>
      </mat-radio-button>
    </mat-radio-group>

    @if (failure(); as message) {
      <p class="failure">{{ message }}</p>
    }
    <button matButton="filled" class="wide" (click)="save()" [disabled]="!canSave()">
      <mat-icon>save</mat-icon>
      @if (problems().length > 0) {
        <span i18n>Fix the marked fields to save</span>
      } @else {
        <span i18n>Save {{ title() }}</span>
      }
    </button>
  `,
})
export class ValueSetEditorComponent {
  private readonly api = inject(ApiService);
  private readonly snackBar = inject(MatSnackBar);

  readonly scope = input.required<SettingsScope>();
  readonly title = input.required<string>();
  readonly keys = input.required<readonly string[]>();
  readonly thisWeek = input.required<Settings>();
  readonly nextWeek = input.required<Settings>();
  readonly thisMonday = input.required<string>();
  readonly nextMonday = input.required<string>();

  /** The server's overview after a save, so the page can show the new state and change log. */
  readonly saved = output<SettingsOverview>();

  protected readonly applyFrom = signal<ApplyFrom>('THIS');
  protected readonly edits = signal<Settings>({});
  protected readonly serverProblems = signal<SettingsProblem[]>([]);
  protected readonly failure = signal<string | null>(null);
  protected readonly busy = signal(false);

  /** What the change starts from: the values in force on the Monday it applies from. */
  protected readonly base = computed(() =>
    this.applyFrom() === 'THIS' ? this.thisWeek() : this.nextWeek(),
  );

  protected readonly values = computed<Settings>(() => ({ ...this.base(), ...this.edits() }));

  protected readonly changedKeys = computed(() => this.keys().filter((key) => this.isChanged(key)));

  protected readonly problems = computed<SettingsProblem[]>(() =>
    this.scope() === 'GLOBAL'
      ? validateThreshold(this.values()[THRESHOLD_KEY])
      : validateValues(this.values()),
  );

  protected readonly canSave = computed(
    () => !this.busy() && this.changedKeys().length > 0 && this.problems().length === 0,
  );

  protected readonly thisLabel = computed(() => longDate(this.thisMonday()));
  protected readonly nextLabel = computed(() => longDate(this.nextMonday()));

  protected edit(key: string, value: unknown): void {
    this.edits.update((current) => ({ ...current, [key]: value === null ? '' : String(value) }));
    // the server's answer was about the numbers as they were; new numbers need a new answer
    this.serverProblems.set([]);
    this.failure.set(null);
  }

  protected isChanged(key: string): boolean {
    const now = this.base()[key];
    const draft = this.values()[key];
    return draft !== undefined && String(draft).trim() !== String(now ?? '').trim();
  }

  protected errorsFor(key: string): string[] {
    const messages = [...this.problems(), ...this.serverProblems()]
      .filter((p) => p.keys.includes(key))
      .map((p) => p.message);
    return [...new Set(messages)];
  }

  /** A value already saved for next week that differs from this week's. */
  protected pending(key: string): string | null {
    const now = this.thisWeek()[key];
    const next = this.nextWeek()[key];
    return this.applyFrom() === 'THIS' && next !== undefined && next !== now ? next : null;
  }

  protected label(key: string): string {
    return SETTING_LABELS[key]?.name ?? key;
  }

  protected unit(key: string): string {
    return SETTING_LABELS[key]?.unit ?? '';
  }

  protected rule(key: string): string {
    return RULE_TEXT[key] ?? '';
  }

  protected async save(): Promise<void> {
    if (!this.canSave()) {
      return;
    }
    this.busy.set(true);
    this.failure.set(null);
    const values: Settings = {};
    for (const key of this.changedKeys()) {
      values[key] = String(this.values()[key]).trim();
    }
    try {
      const overview = await this.api.saveSettings({
        scope: this.scope(),
        values,
        validFrom: this.applyFrom() === 'THIS' ? this.thisMonday() : this.nextMonday(),
      });
      this.edits.set({});
      this.serverProblems.set([]);
      this.snackBar.open($localize`Saved.`, undefined, { duration: 3000 });
      this.saved.emit(overview);
    } catch (error) {
      // the server's message names the numbers, and its problems name the fields
      this.failure.set(errorMessage(error));
      this.serverProblems.set(problemsOf(error));
    } finally {
      this.busy.set(false);
    }
  }
}

function problemsOf(error: unknown): SettingsProblem[] {
  const details = (error as { error?: { details?: { problems?: SettingsProblem[] } } })?.error
    ?.details;
  return Array.isArray(details?.problems) ? details.problems : [];
}

function longDate(date: string): string {
  return new Date(date + 'T00:00:00').toLocaleDateString(undefined, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });
}

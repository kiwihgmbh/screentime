import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { ApiService, errorMessage } from '../../core/api.service';
import { EffectiveSettings, HolidayPeriod, Settings } from '../../core/models';
import { holidayWarning } from '../../core/week-summary';

/**
 * The school holidays, as a plain list with add and edit. Before a period is
 * saved, the form says what it would do to the current week and names the
 * days that change, because a period that starts today changes the numbers
 * the child is looking at right now.
 */
@Component({
  selector: 'app-holiday-periods',
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule],
  styles: `
    ul {
      list-style: none;
      margin: 0 0 12px;
      padding: 0;
    }
    li {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    li.editing {
      background: var(--mat-sys-surface-container);
    }
    .what {
      flex: 1 1 auto;
      min-width: 0;
    }
    .name {
      font-weight: 500;
    }
    .dates {
      font-size: 0.8rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .empty {
      color: var(--mat-sys-on-surface-variant);
    }
    .form {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0 12px;
    }
    .form .full {
      grid-column: 1 / -1;
    }
    .warning {
      display: flex;
      gap: 8px;
      padding: 10px 12px;
      margin-bottom: 12px;
      border-radius: 8px;
      /* the tertiary colour of this theme is green, which reads as "all good" */
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
      font-size: 0.9rem;
    }
    .warning mat-icon {
      flex: 0 0 auto;
    }
    .actions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
    .failure {
      color: var(--mat-sys-error);
    }
  `,
  template: `
    @if (periods().length === 0) {
      <p class="empty" i18n>No holiday periods yet. Add the school holidays below.</p>
    } @else {
      <ul>
        @for (period of periods(); track period.id) {
          <li [class.editing]="editingId() === period.id">
            <div class="what">
              <div class="name">{{ period.name }}</div>
              <div class="dates">
                {{ range(period.startDate, period.endDate) }} ·
                <span i18n>{period.days, plural, =1 {1 day} other {{{ period.days }} days}}</span>
              </div>
            </div>
            @if (confirmingId() === period.id) {
              <button matButton (click)="confirmingId.set(null)" i18n>Keep</button>
              <button matButton="filled" (click)="remove(period)" [disabled]="busy()" i18n>
                Delete
              </button>
            } @else {
              <button
                matIconButton
                (click)="beginEdit(period)"
                [attr.aria-label]="'Change ' + period.name"
              >
                <mat-icon>edit</mat-icon>
              </button>
              <button
                matIconButton
                (click)="confirmingId.set(period.id)"
                [attr.aria-label]="'Delete ' + period.name"
              >
                <mat-icon>delete</mat-icon>
              </button>
            }
          </li>
        }
      </ul>
    }

    <h4>
      @if (editingId() === null) {
        <span i18n>Add a holiday period</span>
      } @else {
        <span i18n>Change {{ originalName() }}</span>
      }
    </h4>
    <div class="form">
      <mat-form-field appearance="outline" class="full">
        <mat-label i18n>Name</mat-label>
        <input
          matInput
          maxlength="64"
          [ngModel]="name()"
          (ngModelChange)="name.set($event)"
          name="holiday-name"
        />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label i18n>First day</mat-label>
        <input
          matInput
          type="date"
          [ngModel]="startDate()"
          (ngModelChange)="startDate.set($event)"
          name="holiday-start"
        />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label i18n>Last day</mat-label>
        <input
          matInput
          type="date"
          [ngModel]="endDate()"
          (ngModelChange)="endDate.set($event)"
          name="holiday-end"
        />
      </mat-form-field>
    </div>
    @if (backwards()) {
      <p class="failure" i18n>The last day is before the first day.</p>
    }
    @if (warning(); as text) {
      <div class="warning" role="status">
        <mat-icon>warning</mat-icon>
        <span>{{ text }}</span>
      </div>
    }
    @if (failure(); as message) {
      <p class="failure">{{ message }}</p>
    }
    <div class="actions">
      <button matButton="filled" (click)="save()" [disabled]="!canSave()">
        <mat-icon>{{ editingId() === null ? 'add' : 'save' }}</mat-icon>
        @if (editingId() === null) {
          <span i18n>Add</span>
        } @else {
          <span i18n>Save changes</span>
        }
      </button>
      @if (editingId() !== null) {
        <button matButton (click)="reset()" i18n>Cancel</button>
      }
    </div>
  `,
})
export class HolidayPeriodsComponent {
  private readonly api = inject(ApiService);

  /** The current week as it stands, to say what a new period would change about it. */
  readonly current = input.required<EffectiveSettings>();
  /** The holiday values in force this week, for "the whole week switches to ..." */
  readonly holidayValues = input.required<Settings>();

  /** Something was added, changed or removed; the page reloads what depends on it. */
  readonly changed = output<void>();

  protected readonly periods = signal<HolidayPeriod[]>([]);
  protected readonly editingId = signal<number | null>(null);
  protected readonly confirmingId = signal<number | null>(null);
  protected readonly name = signal('');
  protected readonly startDate = signal('');
  protected readonly endDate = signal('');
  protected readonly busy = signal(false);
  protected readonly failure = signal<string | null>(null);

  private readonly original = computed(() => this.periods().find((p) => p.id === this.editingId()));
  protected readonly originalName = computed(() => this.original()?.name ?? '');

  protected readonly backwards = computed(
    () => Boolean(this.startDate() && this.endDate()) && this.endDate() < this.startDate(),
  );

  protected readonly canSave = computed(
    () =>
      !this.busy() &&
      this.name().trim().length > 0 &&
      Boolean(this.startDate()) &&
      Boolean(this.endDate()) &&
      !this.backwards(),
  );

  protected readonly warning = computed(() =>
    holidayWarning(
      this.current(),
      this.holidayValues(),
      { startDate: this.startDate(), endDate: this.endDate() },
      this.original(),
    ),
  );

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      this.periods.set(await this.api.holidays());
    } catch (error) {
      this.failure.set(errorMessage(error));
    }
  }

  protected beginEdit(period: HolidayPeriod): void {
    this.editingId.set(period.id);
    this.confirmingId.set(null);
    this.name.set(period.name);
    this.startDate.set(period.startDate);
    this.endDate.set(period.endDate);
    this.failure.set(null);
  }

  protected reset(): void {
    this.editingId.set(null);
    this.name.set('');
    this.startDate.set('');
    this.endDate.set('');
    this.failure.set(null);
  }

  protected async save(): Promise<void> {
    if (!this.canSave()) {
      return;
    }
    const input = {
      name: this.name().trim(),
      startDate: this.startDate(),
      endDate: this.endDate(),
    };
    const id = this.editingId();
    await this.run(async () => {
      if (id === null) {
        await this.api.createHoliday(input);
      } else {
        await this.api.updateHoliday(id, input);
      }
      this.reset();
    });
  }

  protected async remove(period: HolidayPeriod): Promise<void> {
    await this.run(async () => {
      await this.api.deleteHoliday(period.id);
      this.confirmingId.set(null);
      if (this.editingId() === period.id) {
        this.reset();
      }
    });
  }

  private async run(action: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.failure.set(null);
    try {
      await action();
      await this.load();
      this.changed.emit();
    } catch (error) {
      // an overlap comes back as 409 naming the period in the way
      this.failure.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }

  protected range(from: string, to: string): string {
    const options: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'short', year: 'numeric' };
    const a = new Date(from + 'T00:00:00').toLocaleDateString(undefined, options);
    const b = new Date(to + 'T00:00:00').toLocaleDateString(undefined, options);
    return from === to ? a : `${a} – ${b}`;
  }
}

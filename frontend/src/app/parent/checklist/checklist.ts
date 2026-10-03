import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { ApiService, errorMessage } from '../../core/api.service';
import {
  checklistItemProblem,
  describeSchedule,
  MAX_TEXT,
  SHORT,
  WEEK,
} from '../../core/checklist-text';
import { ChecklistItem, ChecklistItemInput, DayOfWeek } from '../../core/models';

/**
 * The reminders the child ticks before screen time. A plain list in the
 * order the child sees it, with add, edit, move and remove. An item is due
 * every day unless weekdays are picked, and only between the dates when they
 * are set; the same date twice is a single day.
 */
@Component({
  selector: 'app-checklist',
  imports: [
    FormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  styles: `
    .stack {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    ul {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    li {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 8px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    li:last-child {
      border-bottom: none;
    }
    li.editing {
      background: var(--mat-sys-surface-container);
    }
    .what {
      flex: 1 1 auto;
      min-width: 0;
    }
    .text {
      font-weight: 500;
      overflow-wrap: anywhere;
    }
    .when {
      font-size: 0.8rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .today {
      font-size: 0.7rem;
      font-weight: 700;
      letter-spacing: 0.04em;
      padding: 1px 6px;
      margin-left: 6px;
      border-radius: 999px;
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .empty,
    .aside {
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.85rem;
    }
    .days {
      display: flex;
      width: 100%;
      margin-bottom: 4px;
    }
    .days mat-button-toggle {
      flex: 1 1 0;
    }
    .dates {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0 12px;
      margin-top: 12px;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .preview {
      font-size: 0.85rem;
      margin: 0 0 12px;
    }
    .failure {
      color: var(--mat-sys-error);
    }
    .wide {
      width: 100%;
    }
  `,
  template: `
    <h2 i18n>Checklist</h2>
    <div class="stack">
      <mat-card
        ><mat-card-content>
          <p class="aside" i18n>
            The child ticks these before every start of screen time, and they come back every time.
            Looking things up is never held back.
          </p>
          @if (items().length === 0) {
            <p class="empty" i18n>No reminders yet.</p>
          } @else {
            <ul>
              @for (item of items(); track item.id; let first = $first; let last = $last) {
                <li [class.editing]="editingId() === item.id">
                  <div class="what">
                    <div class="text">{{ item.text }}</div>
                    <div class="when">
                      {{ schedule(item) }}
                      @if (item.dueToday) {
                        <span class="today" i18n>TODAY</span>
                      }
                    </div>
                  </div>
                  @if (confirmingId() === item.id) {
                    <button matButton (click)="confirmingId.set(null)" i18n>Keep</button>
                    <button matButton="filled" (click)="remove(item)" [disabled]="busy()" i18n>
                      Delete
                    </button>
                  } @else {
                    <button
                      matIconButton
                      (click)="move(item, -1)"
                      [disabled]="busy() || first"
                      [attr.aria-label]="'Move ' + item.text + ' up'"
                    >
                      <mat-icon>arrow_upward</mat-icon>
                    </button>
                    <button
                      matIconButton
                      (click)="move(item, 1)"
                      [disabled]="busy() || last"
                      [attr.aria-label]="'Move ' + item.text + ' down'"
                    >
                      <mat-icon>arrow_downward</mat-icon>
                    </button>
                    <button
                      matIconButton
                      (click)="beginEdit(item)"
                      [attr.aria-label]="'Change ' + item.text"
                    >
                      <mat-icon>edit</mat-icon>
                    </button>
                    <button
                      matIconButton
                      (click)="confirmingId.set(item.id)"
                      [attr.aria-label]="'Delete ' + item.text"
                    >
                      <mat-icon>delete</mat-icon>
                    </button>
                  }
                </li>
              }
            </ul>
          }
        </mat-card-content></mat-card
      >

      <mat-card
        ><mat-card-content>
          <h3>
            @if (editingId() === null) {
              <span i18n>Add a reminder</span>
            } @else {
              <span i18n>Change the reminder</span>
            }
          </h3>
          <mat-form-field appearance="outline" class="wide">
            <mat-label i18n>What to remember</mat-label>
            <input
              matInput
              [maxlength]="maxText"
              [ngModel]="text()"
              (ngModelChange)="text.set($event)"
              name="checklist-text"
            />
            <mat-hint align="end">{{ text().length }} / {{ maxText }}</mat-hint>
          </mat-form-field>

          <div class="aside" i18n>On these days (none picked means every day)</div>
          <mat-button-toggle-group
            class="days"
            multiple
            [value]="weekdays()"
            (change)="weekdays.set($event.value)"
            [attr.aria-label]="'Weekdays'"
            i18n-aria-label
          >
            @for (day of week; track day) {
              <mat-button-toggle [value]="day">{{ short[day] }}</mat-button-toggle>
            }
          </mat-button-toggle-group>

          <div class="dates">
            <mat-form-field appearance="outline">
              <mat-label i18n>First day</mat-label>
              <input
                matInput
                type="date"
                [ngModel]="validFrom()"
                (ngModelChange)="validFrom.set($event)"
                name="checklist-from"
              />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label i18n>Last day</mat-label>
              <input
                matInput
                type="date"
                [ngModel]="validUntil()"
                (ngModelChange)="validUntil.set($event)"
                name="checklist-until"
              />
            </mat-form-field>
          </div>
          <div class="actions">
            <button matButton (click)="todayOnly()" i18n>Today only</button>
            <button matButton (click)="noDates()" [disabled]="!validFrom() && !validUntil()" i18n>
              No dates
            </button>
          </div>

          <p class="preview" data-testid="preview">{{ preview() }}</p>
          @if (problem(); as message) {
            <p class="failure">{{ message }}</p>
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
        </mat-card-content></mat-card
      >
    </div>
  `,
})
export class ChecklistComponent {
  private readonly api = inject(ApiService);

  protected readonly week = WEEK;
  protected readonly short = SHORT;
  protected readonly maxText = MAX_TEXT;

  protected readonly items = signal<ChecklistItem[]>([]);
  protected readonly editingId = signal<number | null>(null);
  protected readonly confirmingId = signal<number | null>(null);
  protected readonly busy = signal(false);
  protected readonly failure = signal<string | null>(null);

  protected readonly text = signal('');
  protected readonly weekdays = signal<DayOfWeek[]>([]);
  protected readonly validFrom = signal('');
  protected readonly validUntil = signal('');

  private readonly input = computed<ChecklistItemInput>(() => ({
    text: this.text(),
    weekdays: this.weekdays(),
    validFrom: this.validFrom() || undefined,
    validUntil: this.validUntil() || undefined,
  }));

  /** Shown only once something is typed: an empty form is not an error yet. */
  protected readonly problem = computed(() =>
    this.text().length === 0 && !this.validFrom() && !this.validUntil()
      ? null
      : checklistItemProblem(this.input()),
  );

  protected readonly preview = computed(() => describeSchedule(this.input()));

  protected readonly canSave = computed(
    () => !this.busy() && checklistItemProblem(this.input()) === null,
  );

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      this.items.set(await this.api.checklist());
    } catch (error) {
      this.failure.set(errorMessage(error));
    }
  }

  protected schedule(item: ChecklistItem): string {
    return describeSchedule(item);
  }

  protected beginEdit(item: ChecklistItem): void {
    this.editingId.set(item.id);
    this.confirmingId.set(null);
    this.text.set(item.text);
    this.weekdays.set([...item.weekdays]);
    this.validFrom.set(item.validFrom ?? '');
    this.validUntil.set(item.validUntil ?? '');
    this.failure.set(null);
  }

  protected reset(): void {
    this.editingId.set(null);
    this.text.set('');
    this.weekdays.set([]);
    this.validFrom.set('');
    this.validUntil.set('');
    this.failure.set(null);
  }

  protected todayOnly(): void {
    const today = localDate(new Date());
    this.validFrom.set(today);
    this.validUntil.set(today);
  }

  protected noDates(): void {
    this.validFrom.set('');
    this.validUntil.set('');
  }

  protected async save(): Promise<void> {
    if (!this.canSave()) {
      return;
    }
    const input = { ...this.input(), text: this.text().trim() };
    const id = this.editingId();
    await this.run(async () => {
      if (id === null) {
        await this.api.createChecklistItem(input);
      } else {
        await this.api.updateChecklistItem(id, input);
      }
      this.reset();
    });
  }

  protected async move(item: ChecklistItem, by: -1 | 1): Promise<void> {
    const ids = this.items().map((i) => i.id);
    const from = ids.indexOf(item.id);
    const to = from + by;
    if (to < 0 || to >= ids.length) {
      return;
    }
    [ids[from], ids[to]] = [ids[to], ids[from]];
    await this.run(async () => {
      this.items.set(await this.api.reorderChecklist(ids));
    });
  }

  protected async remove(item: ChecklistItem): Promise<void> {
    await this.run(async () => {
      await this.api.deleteChecklistItem(item.id);
      this.confirmingId.set(null);
      if (this.editingId() === item.id) {
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
    } catch (error) {
      this.failure.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}

/** The calendar date on this device, not in UTC: "today" as the parent means it. */
function localDate(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

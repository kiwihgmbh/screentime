import { Component, computed, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { ApiService, errorMessage } from '../../core/api.service';
import { EffectiveSettings, SettingChange, SettingsOverview } from '../../core/models';
import { MinutesPipe } from '../../core/minutes.pipe';
import { SETTING_LABELS, THRESHOLD_KEY, VALUE_KEYS } from '../../core/settings-rules';
import { describeWeek } from '../../core/week-summary';
import { HolidayPeriodsComponent } from './holiday-periods';
import { ValueSetEditorComponent } from './value-set-editor';

/**
 * Every number the rules run on, twice: once for term time and once for the
 * holidays, plus the school holidays themselves.
 *
 * Above everything, one sentence says what the current week runs on and why,
 * because that is the question a parent opens this page with. At the bottom,
 * the change log: who changed what, when, and from which value to which.
 */
@Component({
  selector: 'app-settings',
  imports: [MatCardModule, MatIconModule, HolidayPeriodsComponent, ValueSetEditorComponent],
  styles: `
    .stack {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .summary {
      display: flex;
      gap: 10px;
      align-items: flex-start;
      font-size: 1rem;
      line-height: 1.45;
    }
    .summary mat-icon {
      /* a long sentence beside it would otherwise squeeze the icon */
      flex: 0 0 auto;
    }
    .aside {
      font-size: 0.85rem;
      color: var(--mat-sys-on-surface-variant);
      margin: 0 0 8px;
    }
    .log {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .log li {
      padding: 8px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .log li:last-child {
      border-bottom: none;
    }
    .log .meta {
      font-size: 0.78rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .failure {
      color: var(--mat-sys-error);
    }
    h4 {
      margin: 16px 0 4px;
      font-weight: 500;
    }
  `,
  template: `
    <h2 i18n>Settings</h2>
    @if (failure(); as message) {
      <p class="failure">{{ message }}</p>
    }
    @if (overview(); as o) {
      <div class="stack">
        @if (summary(); as text) {
          <mat-card
            ><mat-card-content>
              <div class="summary" data-testid="week-summary">
                <mat-icon>{{
                  effective()?.scope === 'HOLIDAY' ? 'beach_access' : 'school'
                }}</mat-icon>
                <span>{{ text }}</span>
              </div>
            </mat-card-content></mat-card
          >
        }

        <mat-card
          ><mat-card-content>
            <h3 i18n>Term values</h3>
            <p class="aside" i18n>For school weeks.</p>
            <app-value-set-editor
              scope="TERM"
              title="term values"
              i18n-title
              [keys]="valueKeys"
              [thisWeek]="o.term.thisWeek"
              [nextWeek]="o.term.nextWeek"
              [thisMonday]="o.thisWeek"
              [nextMonday]="o.nextWeek"
              (saved)="applied($event)"
            /> </mat-card-content
        ></mat-card>

        <mat-card
          ><mat-card-content>
            <h3 i18n>Holiday values</h3>
            <p class="aside" i18n>
              For a week with {{ o.general.thisWeek[thresholdKey] }} or more holiday days. In other
              weeks, a holiday day still gets the holiday ceiling.
            </p>
            <app-value-set-editor
              scope="HOLIDAY"
              title="holiday values"
              i18n-title
              [keys]="valueKeys"
              [thisWeek]="o.holiday.thisWeek"
              [nextWeek]="o.holiday.nextWeek"
              [thisMonday]="o.thisWeek"
              [nextMonday]="o.nextWeek"
              (saved)="applied($event)"
            /> </mat-card-content
        ></mat-card>

        <mat-card
          ><mat-card-content>
            <h3 i18n>Holiday periods</h3>
            <p class="aside" i18n>
              The school holidays, first and last day included. Nothing is filled in for you.
            </p>
            @if (effective(); as current) {
              <app-holiday-periods
                [current]="current"
                [holidayValues]="o.holiday.thisWeek"
                (changed)="reload()"
              />
            }
            <h4 i18n>When a week counts as holiday</h4>
            <app-value-set-editor
              scope="GLOBAL"
              title="the threshold"
              i18n-title
              [keys]="thresholdKeys"
              [thisWeek]="o.general.thisWeek"
              [nextWeek]="o.general.nextWeek"
              [thisMonday]="o.thisWeek"
              [nextMonday]="o.nextWeek"
              (saved)="applied($event)"
            /> </mat-card-content
        ></mat-card>

        <mat-card
          ><mat-card-content>
            <h3 i18n>Changes</h3>
            @if (o.changes.length === 0) {
              <p class="aside" i18n>Nothing has been changed yet. These are the defaults.</p>
            } @else {
              <ul class="log">
                @for (change of o.changes; track $index) {
                  <li>
                    <div>{{ describe(change) }}</div>
                    <div class="meta">{{ when(change.at) }} · {{ change.by ?? '–' }}</div>
                  </li>
                }
              </ul>
            }
          </mat-card-content></mat-card
        >
      </div>
    }
  `,
})
export class SettingsComponent {
  private readonly api = inject(ApiService);
  private readonly minutes = new MinutesPipe();

  protected readonly valueKeys = VALUE_KEYS;
  protected readonly thresholdKeys = [THRESHOLD_KEY] as const;
  protected readonly thresholdKey = THRESHOLD_KEY;

  protected readonly overview = signal<SettingsOverview | null>(null);
  protected readonly effective = signal<EffectiveSettings | null>(null);
  protected readonly failure = signal<string | null>(null);

  protected readonly summary = computed(() => {
    const week = this.effective();
    return week ? describeWeek(week) : null;
  });

  constructor() {
    void this.reload();
  }

  protected async reload(): Promise<void> {
    try {
      const [overview, effective] = await Promise.all([
        this.api.settings(),
        this.api.effectiveSettings(),
      ]);
      this.overview.set(overview);
      this.effective.set(effective);
      this.failure.set(null);
    } catch (error) {
      this.failure.set(errorMessage(error));
    }
  }

  /** A save answers with the new overview; the sentence for this week may have changed with it. */
  protected async applied(overview: SettingsOverview): Promise<void> {
    this.overview.set(overview);
    try {
      this.effective.set(await this.api.effectiveSettings());
    } catch (error) {
      this.failure.set(errorMessage(error));
    }
  }

  /** One line of the change log, in words. */
  protected describe(change: SettingChange): string {
    if (change.kind === 'HOLIDAY_PERIOD') {
      switch (change.action) {
        case 'CREATE':
          return $localize`Holiday period added: ${change.to}`;
        case 'DELETE':
          return $localize`Holiday period removed: ${change.from}`;
        default:
          return $localize`Holiday period changed: ${change.from} → ${change.to}`;
      }
    }
    const key = change.key ?? '';
    const name = SETTING_LABELS[key]?.name ?? key;
    const set =
      change.scope === 'HOLIDAY'
        ? $localize`Holiday`
        : change.scope === 'GLOBAL'
          ? $localize`General`
          : $localize`Term`;
    const from = change.from === undefined ? '–' : this.value(key, change.from);
    const to = change.to === undefined ? '–' : this.value(key, change.to);
    const since = change.validFrom ? this.day(change.validFrom) : '';
    return $localize`${set}\: ${name} ${from} → ${to}, from ${since}`;
  }

  private value(key: string, raw: string): string {
    if (key === 'cutoffHour') {
      return `${raw}:00`;
    }
    if (key === THRESHOLD_KEY) {
      return $localize`${raw} days`;
    }
    const n = Number(raw);
    return Number.isNaN(n) ? raw : this.minutes.transform(n);
  }

  private day(date: string): string {
    return new Date(date + 'T00:00:00').toLocaleDateString(undefined, {
      weekday: 'short',
      day: 'numeric',
      month: 'short',
    });
  }

  protected when(instant: string): string {
    return new Date(instant).toLocaleString(undefined, {
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
}

import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService, errorMessage } from '../../core/api.service';
import { CheckResult, Device, Week } from '../../core/models';
import { DurationPipe, MinutesPipe } from '../../core/minutes.pipe';
import { WeekPickerComponent } from '../../shared/week-picker';

/**
 * The Sunday comparison.
 *
 * The difference is computed while the parent types, before anything is saved,
 * so the conversation happens over the numbers rather than over a surprise.
 * Saving a second check for the same week replaces the first and reverses the
 * adjustment it wrote; the server guarantees that, and the note below says so.
 */
@Component({
  selector: 'app-check',
  imports: [
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MinutesPipe,
    DurationPipe,
    WeekPickerComponent,
  ],
  styles: `
    .stack {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .device {
      display: grid;
      grid-template-columns: 1fr 120px;
      gap: 12px;
      align-items: center;
    }
    .sum {
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 4px 12px;
      margin: 0;
    }
    .sum dd {
      margin: 0;
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    .sum .total {
      font-weight: 600;
      font-size: 1.1rem;
    }
    .verdict {
      display: flex;
      gap: 10px;
      align-items: flex-start;
      padding: 12px;
      border-radius: 10px;
    }
    .verdict.clean {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .verdict.penalty {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .verdict.look {
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
    }
    .aside {
      font-size: 0.85rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .wide {
      width: 100%;
    }
  `,
  template: `
    <h2 i18n>The weekly check</h2>
    <div class="stack">
      <mat-card
        ><mat-card-content>
          <app-week-picker
            [weekStart]="weekStart()"
            [atLatest]="false"
            (move)="moveWeek($event)"
          /> </mat-card-content
      ></mat-card>

      @if (week(); as w) {
        <mat-card
          ><mat-card-content>
            <h3 i18n>What the log says</h3>
            <dl class="sum">
              <dt i18n>Screen time booked this week</dt>
              <dd class="total">{{ w.balance.weekUsedSeconds | duration }}</dd>
            </dl>
            <p class="aside" i18n>
              Looking things up and film nights are outside the weekly budget, so they are outside
              this comparison. The devices report whole minutes, so the log is compared to the
              nearest minute.
            </p>
          </mat-card-content></mat-card
        >

        <mat-card
          ><mat-card-content>
            <h3 i18n>What the devices report</h3>
            <div class="stack">
              @for (device of devices(); track device.id) {
                <div class="device">
                  <span>{{ device.name }}</span>
                  <mat-form-field appearance="outline">
                    <mat-label i18n>Minutes</mat-label>
                    <input
                      matInput
                      type="number"
                      inputmode="numeric"
                      min="0"
                      [ngModel]="reported()[device.id] ?? 0"
                      (ngModelChange)="setReported(device.id, $event)"
                      [name]="'device-' + device.id"
                    />
                  </mat-form-field>
                </div>
              }
            </div>
            <dl class="sum">
              <dt i18n>Reported</dt>
              <dd>{{ reportedTotal() | minutes }}</dd>
              <dt i18n>Logged</dt>
              <dd>− {{ loggedMinutes() | minutes }}</dd>
              <dt class="total" i18n>Difference</dt>
              <dd class="total">{{ difference() | minutes }}</dd>
            </dl>
          </mat-card-content></mat-card
        >

        <mat-card
          ><mat-card-content>
            <div class="verdict" [class]="verdictKind()">
              <mat-icon>{{ verdictIcon() }}</mat-icon>
              <div>
                @switch (verdictKind()) {
                  @case ('clean') {
                    <span i18n>
                      This is a match. The week is clean and next week gets the bonus.
                    </span>
                  }
                  @case ('penalty') {
                    <span i18n>
                      {{ difference() | minutes }} missing from the log.
                      {{ penalty() | minutes }} will come off next week.
                    </span>
                  }
                  @case ('look') {
                    <span i18n>
                      More is booked than the devices saw. Nothing is deducted and no bonus is
                      given; it is worth looking at the entries.
                    </span>
                  }
                }
              </div>
            </div>

            <mat-checkbox
              [ngModel]="deliberate()"
              (ngModelChange)="deliberate.set($event)"
              name="deliberate"
            >
              <span i18n>The rules were worked around on purpose</span>
            </mat-checkbox>
            <p class="aside" i18n>
              A second account, a changed clock. This adds a deduction and the week never counts as
              clean.
            </p>

            @if (w.check; as existing) {
              <p class="aside" i18n>
                This week was already checked on {{ date(existing.checkedAt) }}. Saving again
                replaces that check and reverses the adjustment it wrote, so nothing stacks.
              </p>
            }

            <button matButton="filled" class="wide" (click)="save()" [disabled]="busy()">
              <mat-icon>save</mat-icon>
              <span i18n>Save the check</span>
            </button>
          </mat-card-content></mat-card
        >

        @if (result(); as r) {
          <mat-card
            ><mat-card-content>
              <h3 i18n>Saved</h3>
              <dl class="sum">
                <dt i18n>Difference</dt>
                <dd>{{ r.check.differenceMinutes | minutes }}</dd>
                <dt i18n>Deduction on {{ r.followingWeek }}</dt>
                <dd>{{ r.check.penaltyMinutes | minutes }}</dd>
                <dt i18n>Bonus next week</dt>
                <dd>{{ r.bonusSetForFollowingWeek ? yes : no }}</dd>
              </dl>
              @if (r.penalty; as penalty) {
                <p class="aside">{{ penalty.reason }}</p>
              }
            </mat-card-content></mat-card
          >
        }
      }
    </div>
  `,
})
export class CheckComponent {
  private readonly api = inject(ApiService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly yes = $localize`yes`;
  protected readonly no = $localize`no`;

  protected readonly weekStart = signal(mondayOf(new Date()));
  protected readonly week = signal<Week | null>(null);
  protected readonly devices = signal<Device[]>([]);
  protected readonly reported = signal<Record<number, number>>({});
  protected readonly deliberate = signal(false);
  protected readonly result = signal<CheckResult | null>(null);
  protected readonly busy = signal(false);
  /** The values of the week being checked, not of today: a week is checked against its own rules. */
  protected readonly settings = signal<Record<string, number>>({});

  protected readonly reportedTotal = computed(() =>
    Object.values(this.reported()).reduce((sum, value) => sum + (Number(value) || 0), 0),
  );

  /** The log to the nearest minute, half up, the same rounding the server uses. */
  protected readonly loggedMinutes = computed(() =>
    Math.round((this.week()?.balance.weekUsedSeconds ?? 0) / 60),
  );

  protected readonly difference = computed(() => this.reportedTotal() - this.loggedMinutes());

  private readonly tolerance = computed(() => Number(this.settings()['toleranceMinutes'] ?? 10));

  protected readonly verdictKind = computed<'clean' | 'penalty' | 'look'>(() => {
    const difference = this.difference();
    if (this.deliberate()) {
      return 'penalty';
    }
    if (Math.abs(difference) <= this.tolerance()) {
      return 'clean';
    }
    return difference > 0 ? 'penalty' : 'look';
  });

  /** The same arithmetic the server does, so the screen cannot promise something else. */
  protected readonly penalty = computed(() => {
    const difference = this.difference();
    const extra = this.deliberate() ? Number(this.settings()['deliberatePenaltyMinutes'] ?? 60) : 0;
    const raw = (difference > this.tolerance() ? difference : 0) + extra;
    return Math.min(raw, Number(this.settings()['maxPenaltyMinutes'] ?? 120));
  });

  protected readonly verdictIcon = computed(() => {
    switch (this.verdictKind()) {
      case 'clean':
        return 'check_circle';
      case 'penalty':
        return 'remove_circle';
      default:
        return 'help';
    }
  });

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      const [week, devices, settings] = await Promise.all([
        this.api.week(this.weekStart()),
        this.api.devices(),
        this.api.effectiveSettings(this.weekStart()),
      ]);
      this.week.set(week);
      this.devices.set(devices);
      this.settings.set(settings.values);
      this.weekStart.set(week.weekStart);
      this.deliberate.set(week.check?.deliberate ?? false);
      // a week that was checked before comes back with what was entered then
      const previous: Record<number, number> = {};
      week.check?.reported.forEach((entry) => (previous[entry.deviceId] = entry.minutes));
      this.reported.set(previous);
      this.result.set(null);
    } catch (error) {
      this.snackBar.open(errorMessage(error), undefined, { duration: 6000 });
    }
  }

  protected moveWeek(direction: number): void {
    const monday = new Date(this.weekStart() + 'T00:00:00');
    monday.setDate(monday.getDate() + direction * 7);
    this.weekStart.set(isoDate(monday));
    void this.load();
  }

  protected setReported(deviceId: number, minutes: number): void {
    this.reported.update((current) => ({ ...current, [deviceId]: Number(minutes) || 0 }));
  }

  protected async save(): Promise<void> {
    this.busy.set(true);
    try {
      const reported = this.devices().map((device) => ({
        deviceId: device.id,
        minutes: Number(this.reported()[device.id] ?? 0),
      }));
      this.result.set(await this.api.saveCheck(this.weekStart(), reported, this.deliberate()));
      await this.load();
    } catch (error) {
      this.snackBar.open(errorMessage(error), undefined, { duration: 6000 });
    } finally {
      this.busy.set(false);
    }
  }

  protected date(instant: string): string {
    return new Date(instant).toLocaleDateString();
  }
}

function mondayOf(date: Date): string {
  const copy = new Date(date);
  const day = (copy.getDay() + 6) % 7;
  copy.setDate(copy.getDate() - day);
  return isoDate(copy);
}

function isoDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')}`;
}

import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService, errorMessage } from '../../core/api.service';
import { Account, SessionType } from '../../core/models';
import { CountdownPipe, MinutesPipe } from '../../core/minutes.pipe';
import { DayStripComponent } from '../../shared/day-strip';
import { SessionListComponent } from '../../shared/session-list';

/**
 * The child's screen.
 *
 * The remaining week is the largest thing on it, because that is the number
 * the week is actually spent against. Everything below it exists so the child
 * can see why that number is what it is: the budget, any correction and its
 * reason, what has been used, the ceiling in force today.
 */
@Component({
  selector: 'app-dashboard',
  imports: [
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MinutesPipe,
    CountdownPipe,
    DayStripComponent,
    SessionListComponent,
  ],
  styles: `
    :host {
      display: block;
    }
    .stack {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .headline {
      text-align: center;
      padding: 8px 0 4px;
    }
    .headline .label {
      font-size: 0.8rem;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--mat-sys-on-surface-variant);
    }
    .headline .value {
      font-size: clamp(3.5rem, 18vw, 6rem);
      font-weight: 300;
      line-height: 1;
      font-variant-numeric: tabular-nums;
    }
    .headline .value.none {
      color: var(--mat-sys-error);
    }
    .headline .of {
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.9rem;
    }

    .pair {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }
    .tile {
      text-align: center;
    }
    .tile .n {
      font-size: 1.7rem;
      font-weight: 400;
      font-variant-numeric: tabular-nums;
    }
    .tile .t {
      font-size: 0.75rem;
      color: var(--mat-sys-on-surface-variant);
    }

    .running {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .running .clock {
      font-size: clamp(2.5rem, 13vw, 4rem);
      font-weight: 300;
      font-variant-numeric: tabular-nums;
      line-height: 1.1;
    }
    .running .clock.over {
      color: var(--mat-sys-error);
    }
    .running .what {
      font-size: 0.85rem;
    }

    .controls {
      display: grid;
      gap: 12px;
    }
    .controls .row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }
    .wide {
      width: 100%;
    }

    .why {
      font-size: 0.85rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .why dl {
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 4px 12px;
      margin: 0;
    }
    .why dd {
      margin: 0;
      font-variant-numeric: tabular-nums;
      text-align: right;
    }
    .why .total {
      font-weight: 600;
      color: var(--mat-sys-on-surface);
    }

    .notice {
      display: flex;
      gap: 8px;
      align-items: flex-start;
      padding: 10px 12px;
      border-radius: 10px;
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
      font-size: 0.85rem;
    }
    .reason {
      font-size: 0.85rem;
    }
    .reason li {
      margin-bottom: 4px;
    }
    .centre {
      display: flex;
      justify-content: center;
      padding: 32px;
    }
    .error {
      color: var(--mat-sys-error);
    }
  `,
  template: `
    @if (loading() && !account()) {
      <div class="centre"><mat-progress-spinner mode="indeterminate" diameter="40" /></div>
    } @else if (failure()) {
      <mat-card
        ><mat-card-content>
          <p class="error">{{ failure() }}</p>
          <button matButton (click)="reload()" i18n>Try again</button>
        </mat-card-content></mat-card
      >
    } @else if (account(); as a) {
      <div class="stack">
        <!-- the number the week is spent against -->
        <div class="headline">
          <div class="label" i18n>Left this week</div>
          <div class="value" [class.none]="a.balance.remainingWeekMinutes === 0">
            {{ a.balance.remainingWeekMinutes | minutes }}
          </div>
          <div class="of" i18n>of {{ a.balance.weeklyBudgetMinutes | minutes }}</div>
        </div>

        <app-day-strip [days]="a.week" />

        <div class="pair">
          <mat-card class="tile"
            ><mat-card-content>
              <div class="n">{{ a.balance.remainingTodayMinutes | minutes }}</div>
              <div class="t" i18n>Left today, of {{ a.balance.dailyCapMinutes | minutes }}</div>
            </mat-card-content></mat-card
          >
          <mat-card class="tile"
            ><mat-card-content>
              <div class="n">{{ a.balance.remainingQuickMinutes | minutes }}</div>
              <div class="t" i18n>Looking things up</div>
            </mat-card-content></mat-card
          >
        </div>

        @if (a.bonusActive) {
          <div class="notice">
            <mat-icon>celebration</mat-icon>
            <span i18n>
              Last week matched, so this week has an extra
              {{ a.balance.weeklyBudgetMinutes - 480 | minutes }} and a higher weekend ceiling.
            </span>
          </div>
        }
        @if (a.holidayWeek) {
          <div class="notice">
            <mat-icon>beach_access</mat-icon>
            <span i18n>Holiday week: every day has the weekend ceiling.</span>
          </div>
        }

        <!-- the running session -->
        @if (a.openSession; as open) {
          <mat-card class="running"
            ><mat-card-content>
              <div class="what">{{ open.deviceName }} · {{ open.type }}</div>
              <div class="clock" [class.over]="secondsLeft() < 0">
                {{ secondsLeft() | countdown }}
              </div>
              <div class="what">
                @if (secondsLeft() >= 0) {
                  <span i18n>left of what is available now</span>
                } @else {
                  <span i18n>over what was available</span>
                }
              </div>
              <button matButton="filled" class="wide" (click)="stop()" [disabled]="busy()">
                <mat-icon>stop</mat-icon>
                <span i18n>Stop</span>
              </button>
            </mat-card-content></mat-card
          >
        } @else {
          <mat-card
            ><mat-card-content class="controls">
              @if (a.screensOff) {
                <div class="notice">
                  <mat-icon>bedtime</mat-icon>
                  <span i18n>
                    Nothing starts after {{ a.cutoffHour }}:00. Sleep matters more than the last
                    round.
                  </span>
                </div>
              }
              <div class="row">
                <mat-form-field appearance="outline">
                  <mat-label i18n>Device</mat-label>
                  <mat-select [(ngModel)]="deviceId">
                    @for (device of a.devices; track device.id) {
                      <mat-option [value]="device.id">{{ device.name }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label i18n>What for</mat-label>
                  <mat-select [(ngModel)]="type">
                    <mat-option value="FUN" i18n>Screen time</mat-option>
                    <mat-option value="QUICK" i18n>Looking something up</mat-option>
                  </mat-select>
                </mat-form-field>
              </div>
              <button
                matButton="filled"
                class="wide"
                (click)="start()"
                [disabled]="busy() || !deviceId()"
              >
                <mat-icon>play_arrow</mat-icon>
                <span i18n>Start</span>
              </button>

              <div class="row">
                <mat-form-field appearance="outline">
                  <mat-label i18n>Or book minutes</mat-label>
                  <input
                    matInput
                    type="number"
                    inputmode="numeric"
                    min="1"
                    [(ngModel)]="manualMinutes"
                  />
                </mat-form-field>
                <button
                  matButton
                  class="wide"
                  (click)="book()"
                  [disabled]="busy() || !deviceId() || !manualMinutes()"
                >
                  <span i18n>Book</span>
                </button>
              </div>
            </mat-card-content></mat-card
          >
        }

        <!-- why the number is what it is -->
        <mat-card
          ><mat-card-content>
            <h3 i18n>This week, in numbers</h3>
            <div class="why">
              <dl>
                <dt i18n>Budget</dt>
                <dd>{{ a.balance.weeklyBudgetMinutes | minutes }}</dd>
                @if (a.balance.adjustmentMinutes !== 0) {
                  <dt i18n>Corrections</dt>
                  <dd>{{ a.balance.adjustmentMinutes | minutes }}</dd>
                }
                <dt i18n>Used</dt>
                <dd>− {{ a.balance.weekUsedMinutes | minutes }}</dd>
                <dt class="total" i18n>Left</dt>
                <dd class="total">{{ a.balance.remainingWeekMinutes | minutes }}</dd>
              </dl>
              @if (a.weekAdjustments.length > 0) {
                <ul class="reason">
                  @for (adjustment of a.weekAdjustments; track adjustment.id) {
                    <li>
                      <strong>{{ adjustment.minutes | minutes }}</strong> — {{ adjustment.reason }}
                    </li>
                  }
                </ul>
              }
            </div>
          </mat-card-content></mat-card
        >

        <mat-card
          ><mat-card-content>
            <h3 i18n>Today</h3>
            <app-session-list [sessions]="a.todayEntries" /> </mat-card-content
        ></mat-card>
      </div>
    }
  `,
})
export class DashboardComponent implements OnDestroy {
  private readonly api = inject(ApiService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly account = signal<Account | null>(null);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly failure = signal<string | null>(null);

  protected readonly deviceId = signal<number | null>(null);
  protected readonly type = signal<SessionType>('FUN');
  protected readonly manualMinutes = signal<number | null>(null);

  /** Ticks once a second so the countdown moves without re-asking the server. */
  private readonly nowMs = signal(Date.now());
  private readonly ticker = setInterval(() => this.nowMs.set(Date.now()), 1000);

  /**
   * What is left of the budget this session is spending. Counted down from the
   * server's start instant, so a reload does not reset it.
   */
  protected readonly secondsLeft = computed(() => {
    const open = this.account()?.openSession;
    if (!open) {
      return 0;
    }
    const elapsedSeconds = Math.floor((this.nowMs() - new Date(open.startedAt).getTime()) / 1000);
    return open.countdownAgainstMinutes * 60 - elapsedSeconds;
  });

  constructor() {
    void this.reload();
  }

  ngOnDestroy(): void {
    clearInterval(this.ticker);
  }

  protected async reload(): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    try {
      const account = await this.api.account();
      this.account.set(account);
      if (this.deviceId() === null && account.devices.length > 0) {
        this.deviceId.set(account.devices[0].id);
      }
    } catch (error) {
      this.failure.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }

  protected async start(): Promise<void> {
    const device = this.deviceId();
    if (device === null) {
      return;
    }
    await this.run(() => this.api.start(device, this.type()));
  }

  protected async stop(): Promise<void> {
    await this.run(() => this.api.stop());
  }

  protected async book(): Promise<void> {
    const device = this.deviceId();
    const minutes = this.manualMinutes();
    if (device === null || !minutes) {
      return;
    }
    await this.run(async () => {
      await this.api.bookManually({ minutes, deviceId: device, type: this.type() });
      this.manualMinutes.set(null);
    });
  }

  /**
   * Runs one action and reloads. The server's message is shown as it is: it is
   * written to be read by the child, and a balance that changes without a
   * reason it can read is how cooperation is lost.
   */
  private async run(action: () => Promise<unknown>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
      await this.reload();
    } catch (error) {
      this.snackBar.open(errorMessage(error), undefined, { duration: 6000 });
    } finally {
      this.busy.set(false);
    }
  }
}

import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ApiService, errorMessage } from '../../core/api.service';
import { WeekSummary } from '../../core/models';
import { MinutesPipe } from '../../core/minutes.pipe';

/** Past weeks, read only, with the outcome of each check spelled out. */
@Component({
  selector: 'app-history',
  imports: [MatCardModule, MatIconModule, MatButtonModule, MatProgressSpinnerModule, MinutesPipe],
  styles: `
    .stack {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .week {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .dates {
      flex: 1 1 auto;
    }
    .dates .range {
      font-weight: 600;
    }
    .dates .detail {
      font-size: 0.78rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .used {
      text-align: right;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
    .used .big {
      font-size: 1.1rem;
      font-weight: 500;
    }
    .tag {
      font-size: 0.7rem;
      font-weight: 700;
      letter-spacing: 0.05em;
      padding: 2px 8px;
      border-radius: 999px;
      white-space: nowrap;
    }
    .tag.clean {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .tag.penalty {
      background: var(--mat-sys-error-container);
      color: var(--mat-sys-on-error-container);
    }
    .tag.open {
      background: var(--mat-sys-surface-variant);
      color: var(--mat-sys-on-surface-variant);
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
    <h2 i18n>Past weeks</h2>
    @if (loading()) {
      <div class="centre"><mat-progress-spinner mode="indeterminate" diameter="40" /></div>
    } @else if (failure()) {
      <p class="error">{{ failure() }}</p>
      <button matButton (click)="reload()" i18n>Try again</button>
    } @else {
      <div class="stack">
        @for (week of weeks(); track week.weekStart) {
          <mat-card
            ><mat-card-content class="week">
              <div class="dates">
                <div class="range">{{ range(week.weekStart) }}</div>
                <div class="detail">
                  <span i18n>budget {{ week.budgetMinutes | minutes }}</span>
                  @if (week.adjustmentMinutes !== 0) {
                    <span i18n>, corrections {{ week.adjustmentMinutes | minutes }}</span>
                  }
                  @if (week.holidayWeek) {
                    <span i18n>, holiday week</span>
                  }
                  @if (week.bonusActive) {
                    <span i18n>, bonus</span>
                  }
                </div>
              </div>
              <div class="used">
                <div class="big">{{ week.usedMinutes | minutes }}</div>
                <div class="detail" i18n>used</div>
              </div>
              @if (week.check; as check) {
                @if (check.clean) {
                  <span class="tag clean" i18n>matched</span>
                } @else {
                  <span class="tag penalty">
                    @if (check.penaltyMinutes > 0) {
                      <span i18n>− {{ check.penaltyMinutes | minutes }}</span>
                    } @else {
                      <span i18n>checked</span>
                    }
                  </span>
                }
              } @else {
                <span class="tag open" i18n>not checked</span>
              }
            </mat-card-content></mat-card
          >
        }
      </div>
    }
  `,
})
export class HistoryComponent {
  private readonly api = inject(ApiService);

  protected readonly weeks = signal<WeekSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly failure = signal<string | null>(null);

  constructor() {
    void this.reload();
  }

  protected async reload(): Promise<void> {
    this.loading.set(true);
    this.failure.set(null);
    try {
      this.weeks.set(await this.api.history(12));
    } catch (error) {
      this.failure.set(errorMessage(error));
    } finally {
      this.loading.set(false);
    }
  }

  protected range(weekStart: string): string {
    const monday = new Date(weekStart + 'T00:00:00');
    const sunday = new Date(monday);
    sunday.setDate(sunday.getDate() + 6);
    const fmt = (d: Date) => d.toLocaleDateString(undefined, { day: '2-digit', month: 'short' });
    return `${fmt(monday)} – ${fmt(sunday)}`;
  }
}

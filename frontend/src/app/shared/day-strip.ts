import { Component, computed, input } from '@angular/core';
import { Day } from '../core/models';
import { MinutesPipe } from '../core/minutes.pipe';

/**
 * The seven days of the week as one row, today marked.
 *
 * The bar shows how much of a day's ceiling is gone. A day that is already
 * past and was not used is not a loss to display loudly: unused time expires,
 * and the point is not to save but to decide.
 */
@Component({
  selector: 'app-day-strip',
  imports: [MinutesPipe],
  styles: `
    .strip {
      display: grid;
      grid-template-columns: repeat(7, 1fr);
      gap: 6px;
    }
    .day {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 4px;
      padding: 8px 2px;
      border-radius: 10px;
      background: var(--mat-sys-surface-container-low);
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.75rem;
      line-height: 1.2;
      text-align: center;
    }
    .day.today {
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
      outline: 2px solid var(--mat-sys-primary);
    }
    .day.future {
      opacity: 0.6;
    }
    .name {
      font-weight: 600;
      letter-spacing: 0.02em;
    }
    .bar {
      width: 100%;
      height: 5px;
      border-radius: 3px;
      background: var(--mat-sys-surface-variant);
      overflow: hidden;
    }
    .fill {
      height: 100%;
      background: var(--mat-sys-primary);
    }
    .fill.full {
      background: var(--mat-sys-error);
    }
    .left {
      font-variant-numeric: tabular-nums;
    }
  `,
  template: `
    <div class="strip" role="list">
      @for (day of days(); track day.date) {
        <div
          class="day"
          [class.today]="day.today"
          [class.future]="day.future"
          role="listitem"
          [attr.data-date]="day.date"
          [attr.aria-current]="day.today ? 'date' : null"
        >
          <span class="name">{{ shortName(day.dayOfWeek) }}</span>
          <div class="bar">
            <div
              class="fill"
              [class.full]="day.remainingMinutes === 0"
              [style.width.%]="usedShare(day)"
            ></div>
          </div>
          <span class="left">{{ day.remainingMinutes | minutes }}</span>
        </div>
      }
    </div>
  `,
})
export class DayStripComponent {
  readonly days = input.required<Day[]>();

  protected shortName(day: string): string {
    return day.slice(0, 3).charAt(0) + day.slice(1, 3).toLowerCase();
  }

  protected usedShare(day: Day): number {
    if (day.capMinutes <= 0) {
      return 0;
    }
    return Math.min(100, Math.round((day.usedMinutes / day.capMinutes) * 100));
  }
}

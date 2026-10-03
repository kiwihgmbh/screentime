import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

/** Monday to Monday, one week at a time. */
@Component({
  selector: 'app-week-picker',
  imports: [MatButtonModule, MatIconModule],
  styles: `
    .picker {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
    .label {
      font-weight: 600;
    }
  `,
  template: `
    <div class="picker">
      <button
        matIconButton
        (click)="move.emit(-1)"
        [attr.aria-label]="'Previous week'"
        i18n-aria-label
      >
        <mat-icon>chevron_left</mat-icon>
      </button>
      <span class="label">{{ label() }}</span>
      <button
        matIconButton
        (click)="move.emit(1)"
        [disabled]="atLatest()"
        [attr.aria-label]="'Next week'"
        i18n-aria-label
      >
        <mat-icon>chevron_right</mat-icon>
      </button>
    </div>
  `,
})
export class WeekPickerComponent {
  readonly weekStart = input.required<string>();
  readonly atLatest = input(false);
  readonly move = output<number>();

  protected label(): string {
    const monday = new Date(this.weekStart() + 'T00:00:00');
    const sunday = new Date(monday);
    sunday.setDate(sunday.getDate() + 6);
    const format = (d: Date) => d.toLocaleDateString(undefined, { day: '2-digit', month: 'short' });
    return `${format(monday)} – ${format(sunday)}`;
  }
}

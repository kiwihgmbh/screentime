import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Session } from '../core/models';
import { DurationPipe } from '../core/minutes.pipe';

/**
 * A list of entries. The child sees it without controls, which is the point:
 * the log is a record, not something to tidy up.
 */
@Component({
  selector: 'app-session-list',
  imports: [DurationPipe, MatButtonModule, MatIconModule],
  styles: `
    ul {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    li {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    li:last-child {
      border-bottom: none;
    }
    .type {
      flex: 0 0 auto;
      font-size: 0.7rem;
      font-weight: 700;
      letter-spacing: 0.06em;
      padding: 2px 7px;
      border-radius: 999px;
      background: var(--mat-sys-secondary-container);
      color: var(--mat-sys-on-secondary-container);
    }
    .type.QUICK {
      background: var(--mat-sys-tertiary-container);
      color: var(--mat-sys-on-tertiary-container);
    }
    .type.FILM {
      background: var(--mat-sys-surface-variant);
      color: var(--mat-sys-on-surface-variant);
    }
    .what {
      flex: 1 1 auto;
      min-width: 0;
    }
    .device {
      font-weight: 500;
    }
    .meta {
      font-size: 0.75rem;
      color: var(--mat-sys-on-surface-variant);
    }
    .minutes {
      font-variant-numeric: tabular-nums;
      font-weight: 600;
      white-space: nowrap;
    }
    .empty {
      color: var(--mat-sys-on-surface-variant);
      padding: 12px 0;
    }
    .flag {
      font-size: 0.75rem;
      color: var(--mat-sys-error);
    }
  `,
  template: `
    @if (sessions().length === 0) {
      <p class="empty" i18n>Nothing booked yet.</p>
    } @else {
      <ul>
        @for (session of sessions(); track session.id) {
          <li>
            <span class="type" [class]="session.type">{{ session.type }}</span>
            <div class="what">
              <div class="device">{{ session.deviceName }}</div>
              <div class="meta">
                {{ time(session.startedAt) }}
                @if (session.source === 'MANUAL') {
                  <span i18n>· booked by hand</span>
                }
                @if (session.createdBy) {
                  <span>· {{ session.createdBy }}</span>
                }
                @if (session.note) {
                  <span>· {{ session.note }}</span>
                }
              </div>
              @if (session.autoClosed) {
                <div class="flag" i18n>
                  Stopped automatically at midnight, capped at what the day had left.
                </div>
              }
            </div>
            <span class="minutes">
              @if (session.running) {
                <span i18n>running</span>
              } @else {
                {{ session.seconds | duration }}
              }
            </span>
            @if (editable()) {
              <button
                matIconButton
                (click)="edit.emit(session)"
                [attr.aria-label]="'Correct this entry'"
                i18n-aria-label
              >
                <mat-icon>edit</mat-icon>
              </button>
              <button
                matIconButton
                (click)="remove.emit(session)"
                [attr.aria-label]="'Delete this entry'"
                i18n-aria-label
              >
                <mat-icon>delete</mat-icon>
              </button>
            }
          </li>
        }
      </ul>
    }
  `,
})
export class SessionListComponent {
  readonly sessions = input.required<Session[]>();
  /** Only a parent gets controls. A child's list is read only, by design. */
  readonly editable = input(false);

  readonly edit = output<Session>();
  readonly remove = output<Session>();

  protected time(instant: string): string {
    return new Date(instant).toLocaleTimeString(undefined, {
      hour: '2-digit',
      minute: '2-digit',
    });
  }
}

import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApiService, errorMessage } from '../../core/api.service';
import { Device, Session, SessionType, Week } from '../../core/models';
import { MinutesPipe } from '../../core/minutes.pipe';
import { SessionListComponent } from '../../shared/session-list';
import { WeekPickerComponent } from '../../shared/week-picker';

/**
 * One week, day by day, with the controls a parent needs: correct an entry,
 * delete one, book a day that was missed, adjust the week with a reason, and
 * mark the week as a holiday week.
 */
@Component({
  selector: 'app-parent-week',
  imports: [
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MinutesPipe,
    SessionListComponent,
    WeekPickerComponent,
  ],
  styles: `
    .stack {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
    }
    .row3 {
      display: grid;
      grid-template-columns: 1fr 1fr auto;
      gap: 12px;
      align-items: start;
    }
    .wide {
      width: 100%;
    }
    .day-head {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 8px;
    }
    .day-head .name {
      font-weight: 600;
    }
    .day-head .numbers {
      font-size: 0.8rem;
      color: var(--mat-sys-on-surface-variant);
      font-variant-numeric: tabular-nums;
    }
    .today {
      outline: 2px solid var(--mat-sys-primary);
    }
    .sum {
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 4px 12px;
      margin: 0 0 8px;
    }
    .sum dd {
      margin: 0;
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    .sum .total {
      font-weight: 600;
    }
    .adjustment {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.9rem;
      padding: 6px 0;
      border-bottom: 1px solid var(--mat-sys-outline-variant);
    }
    .adjustment .m {
      font-weight: 600;
      font-variant-numeric: tabular-nums;
    }
    .adjustment .r {
      flex: 1 1 auto;
    }
    .aside {
      font-size: 0.85rem;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
  template: `
    <h2 i18n>The week</h2>
    <div class="stack">
      <mat-card
        ><mat-card-content>
          <app-week-picker [weekStart]="weekStart()" (move)="moveWeek($event)" /> </mat-card-content
      ></mat-card>

      @if (week(); as w) {
        <mat-card
          ><mat-card-content>
            <dl class="sum">
              <dt i18n>Budget</dt>
              <dd>{{ w.balance.weeklyBudgetMinutes | minutes }}</dd>
              <dt i18n>Corrections</dt>
              <dd>{{ w.balance.adjustmentMinutes | minutes }}</dd>
              <dt i18n>Used</dt>
              <dd>− {{ w.balance.weekUsedMinutes | minutes }}</dd>
              <dt class="total" i18n>Left</dt>
              <dd class="total">{{ w.balance.remainingWeekMinutes | minutes }}</dd>
            </dl>
            <mat-checkbox
              [ngModel]="w.holidayWeek"
              (ngModelChange)="setHoliday($event)"
              name="holiday"
            >
              <span i18n>Holiday week: the weekend ceiling applies every day</span>
            </mat-checkbox>
            @if (w.bonusActive) {
              <p class="aside" i18n>
                The bonus is active this week because the week before it matched.
              </p>
            }
          </mat-card-content></mat-card
        >

        <!-- corrections to the week -->
        <mat-card
          ><mat-card-content>
            <h3 i18n>Corrections</h3>
            @for (adjustment of w.adjustments; track adjustment.id) {
              <div class="adjustment">
                <span class="m">{{ adjustment.minutes | minutes }}</span>
                <span class="r">{{ adjustment.reason }}</span>
                @if (adjustment.fromWeeklyCheck) {
                  <span class="aside" i18n>from the check</span>
                } @else {
                  <button
                    matIconButton
                    (click)="removeAdjustment(adjustment.id)"
                    [attr.aria-label]="'Remove this correction'"
                    i18n-aria-label
                  >
                    <mat-icon>delete</mat-icon>
                  </button>
                }
              </div>
            }
            <div class="row3">
              <mat-form-field appearance="outline">
                <mat-label i18n>Minutes, + or −</mat-label>
                <input matInput type="number" [(ngModel)]="adjustMinutes" name="adjustMinutes" />
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label i18n>Reason</mat-label>
                <input matInput [(ngModel)]="adjustReason" name="adjustReason" />
              </mat-form-field>
              <button
                matButton
                (click)="addAdjustment()"
                [disabled]="busy() || !adjustMinutes() || !adjustReason()"
              >
                <span i18n>Add</span>
              </button>
            </div>
            <p class="aside" i18n>
              The reason is shown to the child on their own screen. A balance that drops without an
              explanation is the fastest way to lose their cooperation.
            </p>
          </mat-card-content></mat-card
        >

        <!-- book a day that was missed -->
        <mat-card
          ><mat-card-content>
            <h3 i18n>Book time for a day</h3>
            <div class="row">
              <mat-form-field appearance="outline">
                <mat-label i18n>Day</mat-label>
                <mat-select [(ngModel)]="bookDate" name="bookDate">
                  @for (day of w.days; track day.date) {
                    <mat-option [value]="day.date">{{ dayLabel(day.date) }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label i18n>Minutes</mat-label>
                <input
                  matInput
                  type="number"
                  min="1"
                  [(ngModel)]="bookMinutes"
                  name="bookMinutes"
                />
              </mat-form-field>
            </div>
            <div class="row">
              <mat-form-field appearance="outline">
                <mat-label i18n>Device</mat-label>
                <mat-select [(ngModel)]="bookDevice" name="bookDevice">
                  @for (device of devices(); track device.id) {
                    <mat-option [value]="device.id">{{ device.name }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="outline">
                <mat-label i18n>Type</mat-label>
                <mat-select [(ngModel)]="bookType" name="bookType">
                  <mat-option value="FUN" i18n>Screen time</mat-option>
                  <mat-option value="QUICK" i18n>Looking something up</mat-option>
                  <mat-option value="FILM" i18n>Film night</mat-option>
                </mat-select>
              </mat-form-field>
            </div>
            <mat-form-field appearance="outline" class="wide">
              <mat-label i18n>Note</mat-label>
              <input matInput [(ngModel)]="bookNote" name="bookNote" />
            </mat-form-field>
            <button
              matButton="filled"
              class="wide"
              (click)="book()"
              [disabled]="busy() || !bookDate() || !bookMinutes() || !bookDevice()"
            >
              <span i18n>Book</span>
            </button>
          </mat-card-content></mat-card
        >

        <!-- the days -->
        @for (day of w.days; track day.date) {
          <mat-card [class.today]="day.today"
            ><mat-card-content>
              <div class="day-head">
                <span class="name">{{ dayLabel(day.date) }}</span>
                <span class="numbers">
                  {{ day.usedMinutes | minutes }} / {{ day.capMinutes | minutes }}
                  @if (day.quickUsedMinutes > 0) {
                    <span i18n>· {{ day.quickUsedMinutes | minutes }} looking up</span>
                  }
                </span>
              </div>
              <app-session-list
                [sessions]="day.entries"
                [editable]="true"
                (edit)="beginEdit($event)"
                (remove)="removeSession($event)"
              /> </mat-card-content
          ></mat-card>
        }

        <!-- correcting one entry -->
        @if (editing(); as entry) {
          <mat-card
            ><mat-card-content>
              <h3 i18n>Correct the entry on {{ dayLabel(entry.day) }}</h3>
              <div class="row">
                <mat-form-field appearance="outline">
                  <mat-label i18n>Minutes</mat-label>
                  <input
                    matInput
                    type="number"
                    min="0"
                    [(ngModel)]="editMinutes"
                    name="editMinutes"
                  />
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label i18n>Day</mat-label>
                  <mat-select [(ngModel)]="editDate" name="editDate">
                    @for (day of w.days; track day.date) {
                      <mat-option [value]="day.date">{{ dayLabel(day.date) }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              </div>
              <div class="row">
                <mat-form-field appearance="outline">
                  <mat-label i18n>Device</mat-label>
                  <mat-select [(ngModel)]="editDevice" name="editDevice">
                    @for (device of devices(); track device.id) {
                      <mat-option [value]="device.id">{{ device.name }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label i18n>Type</mat-label>
                  <mat-select [(ngModel)]="editType" name="editType">
                    <mat-option value="FUN" i18n>Screen time</mat-option>
                    <mat-option value="QUICK" i18n>Looking something up</mat-option>
                    <mat-option value="FILM" i18n>Film night</mat-option>
                  </mat-select>
                </mat-form-field>
              </div>
              <mat-form-field appearance="outline" class="wide">
                <mat-label i18n>Note</mat-label>
                <input matInput [(ngModel)]="editNote" name="editNote" />
              </mat-form-field>
              <div class="row">
                <button matButton="filled" (click)="saveEdit()" [disabled]="busy()" i18n>
                  Save
                </button>
                <button matButton (click)="editing.set(null)" i18n>Cancel</button>
              </div>
            </mat-card-content></mat-card
          >
        }
      }
    </div>
  `,
})
export class ParentWeekComponent {
  private readonly api = inject(ApiService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly weekStart = signal(mondayOf(new Date()));
  protected readonly week = signal<Week | null>(null);
  protected readonly devices = signal<Device[]>([]);
  protected readonly busy = signal(false);

  protected readonly adjustMinutes = signal<number | null>(null);
  protected readonly adjustReason = signal('');

  protected readonly bookDate = signal<string | null>(null);
  protected readonly bookMinutes = signal<number | null>(null);
  protected readonly bookDevice = signal<number | null>(null);
  protected readonly bookType = signal<SessionType>('FUN');
  protected readonly bookNote = signal('');

  protected readonly editing = signal<Session | null>(null);
  protected readonly editMinutes = signal<number | null>(null);
  protected readonly editDate = signal<string | null>(null);
  protected readonly editDevice = signal<number | null>(null);
  protected readonly editType = signal<SessionType>('FUN');
  protected readonly editNote = signal('');

  protected readonly today = computed(() => this.week()?.days.find((d) => d.today)?.date ?? null);

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      const [week, devices] = await Promise.all([
        this.api.week(this.weekStart()),
        this.api.devices(),
      ]);
      this.week.set(week);
      this.devices.set(devices);
      this.weekStart.set(week.weekStart);
      this.bookDate.set(this.bookDate() ?? week.days.find((d) => d.today)?.date ?? week.weekStart);
      this.bookDevice.set(this.bookDevice() ?? devices[0]?.id ?? null);
    } catch (error) {
      this.snackBar.open(errorMessage(error), undefined, { duration: 6000 });
    }
  }

  protected moveWeek(direction: number): void {
    const monday = new Date(this.weekStart() + 'T00:00:00');
    monday.setDate(monday.getDate() + direction * 7);
    this.weekStart.set(isoDate(monday));
    this.editing.set(null);
    void this.load();
  }

  protected dayLabel(date: string): string {
    return new Date(date + 'T00:00:00').toLocaleDateString(undefined, {
      weekday: 'long',
      day: '2-digit',
      month: 'short',
    });
  }

  protected beginEdit(session: Session): void {
    this.editing.set(session);
    this.editMinutes.set(session.minutes);
    this.editDate.set(session.day);
    this.editDevice.set(session.deviceId);
    this.editType.set(session.type);
    this.editNote.set(session.note ?? '');
  }

  protected async saveEdit(): Promise<void> {
    const entry = this.editing();
    if (!entry) {
      return;
    }
    await this.run(async () => {
      await this.api.updateSession(entry.id, {
        minutes: this.editMinutes() ?? undefined,
        deviceId: this.editDevice() ?? undefined,
        type: this.editType(),
        date: this.editDate() ?? undefined,
        note: this.editNote(),
      });
      this.editing.set(null);
    });
  }

  protected async removeSession(session: Session): Promise<void> {
    await this.run(() => this.api.deleteSession(session.id));
  }

  protected async addAdjustment(): Promise<void> {
    const minutes = this.adjustMinutes();
    const reason = this.adjustReason();
    if (!minutes || !reason.trim()) {
      return;
    }
    await this.run(async () => {
      await this.api.addAdjustment(this.weekStart(), minutes, reason.trim());
      this.adjustMinutes.set(null);
      this.adjustReason.set('');
    });
  }

  protected async removeAdjustment(id: number): Promise<void> {
    await this.run(() => this.api.deleteAdjustment(id));
  }

  protected async setHoliday(holiday: boolean): Promise<void> {
    await this.run(() => this.api.setHoliday(this.weekStart(), holiday));
  }

  protected async book(): Promise<void> {
    const date = this.bookDate();
    const minutes = this.bookMinutes();
    const deviceId = this.bookDevice();
    if (!date || !minutes || !deviceId) {
      return;
    }
    await this.run(async () => {
      await this.api.bookManually({
        minutes,
        deviceId,
        type: this.bookType(),
        date,
        note: this.bookNote() || undefined,
      });
      this.bookMinutes.set(null);
      this.bookNote.set('');
    });
  }

  private async run(action: () => Promise<unknown>): Promise<void> {
    this.busy.set(true);
    try {
      await action();
      await this.load();
    } catch (error) {
      this.snackBar.open(errorMessage(error), undefined, { duration: 6000 });
    } finally {
      this.busy.set(false);
    }
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

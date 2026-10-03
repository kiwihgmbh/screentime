import { Component, computed, inject, signal } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { ApiService } from '../../core/api.service';
import { EffectiveSettings } from '../../core/models';
import { MinutesPipe } from '../../core/minutes.pipe';
import { weekReason } from '../../core/week-summary';

/**
 * The rules in plain language, on their own page.
 *
 * The numbers are read from the values in force this week, not written into
 * the text, so this page cannot drift away from what the app does. A rules
 * page that disagrees with the balance is worse than none. The first card
 * says why the numbers are what they are: term rules or holiday rules, and
 * which days are holidays.
 */
@Component({
  selector: 'app-rules',
  imports: [MatCardModule, MatIconModule, MinutesPipe],
  styles: `
    .stack {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    h3 {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 0 0 6px;
    }
    h3 mat-icon {
      flex: 0 0 auto;
    }
    p {
      margin: 0 0 8px;
      line-height: 1.5;
    }
    p:last-child {
      margin-bottom: 0;
    }
    .aside {
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.9rem;
    }
  `,
  template: `
    <h2 i18n>The rules</h2>
    <div class="stack">
      @if (settings(); as week) {
        <mat-card class="why"
          ><mat-card-content>
            <h3>
              <mat-icon>{{ week.scope === 'HOLIDAY' ? 'beach_access' : 'school' }}</mat-icon>
              @if (week.scope === 'HOLIDAY') {
                <span i18n>Holiday rules this week</span>
              } @else {
                <span i18n>Term rules this week</span>
              }
            </h3>
            <p data-testid="reason">{{ reason() }}</p>
            <p class="aside" i18n>
              The numbers below are the ones in force this week. A week with
              {{ week.holidayWeekThresholdDays }} or more holiday days runs on the holiday rules.
            </p>
          </mat-card-content></mat-card
        >
      }
      <mat-card
        ><mat-card-content>
          <h3><mat-icon>schedule</mat-icon><span i18n>The budget</span></h3>
          <p i18n>
            {{ number('weeklyMinutes') | minutes }} a week, Monday to Sunday. At most
            {{ number('weekdayCapMinutes') | minutes }} on a school day and
            {{ number('weekendCapMinutes') | minutes }} on Saturday and Sunday.
          </p>
          @if (holidayDaysInTermWeek()) {
            <p i18n>On a holiday day the holiday ceiling applies instead, as it says above.</p>
          }
          <p i18n>
            The daily ceilings add up to more than the week on purpose: you have to choose how to
            spend it.
          </p>
          <p class="aside" i18n>
            Unused time is gone. It does not move to tomorrow and it does not move to next week. The
            point is not to save, it is to decide.
          </p>
        </mat-card-content></mat-card
      >

      <mat-card
        ><mat-card-content>
          <h3><mat-icon>search</mat-icon><span i18n>Looking things up</span></h3>
          <p i18n>
            {{ number('quickDailyMinutes') | minutes }} a day, outside the weekly budget, no need to
            ask.
          </p>
          <p class="aside" i18n>
            Looking something up is a question with an answer, and it ends when the answer is there.
            Watching a video about the same subject is not looking it up. School work is separate
            and does not come out of any of this.
          </p>
        </mat-card-content></mat-card
      >

      <mat-card
        ><mat-card-content>
          <h3><mat-icon>bedtime</mat-icon><span i18n>Evenings</span></h3>
          <p i18n>
            Nothing starts after {{ number('cutoffHour') }}. Sleep matters more than the last round.
          </p>
        </mat-card-content></mat-card
      >

      <mat-card
        ><mat-card-content>
          <h3><mat-icon>movie</mat-icon><span i18n>Film nights</span></h3>
          <p i18n>
            On Friday, Saturday and Sunday the family watches a film together. It does not count.
          </p>
          <p class="aside" i18n>
            It is a film and not a series, everyone picks it together, and at least one parent
            watches. Watching on alone counts again.
          </p>
        </mat-card-content></mat-card
      >

      <mat-card
        ><mat-card-content>
          <h3><mat-icon>fact_check</mat-icon><span i18n>The weekly check</span></h3>
          <p i18n>
            Once a week, on Sunday, the log is compared with what the devices report. A difference
            of up to {{ number('toleranceMinutes') | minutes }} still counts as a match.
          </p>
          <p i18n>
            A week that matches earns an extra {{ number('bonusMinutes') | minutes }} the week
            after, with a weekend ceiling of {{ number('bonusWeekendCapMinutes') | minutes }} so the
            extra time can actually be used.
          </p>
          <p i18n>
            Time that is missing from the log comes off the following week. Going around the rules
            on purpose costs
            {{ number('deliberatePenaltyMinutes') | minutes }} more. No week can lose more than
            {{ number('maxPenaltyMinutes') | minutes }}, because a week with nothing left to lose
            has nothing left to protect.
          </p>
        </mat-card-content></mat-card
      >

      <mat-card
        ><mat-card-content>
          <h3><mat-icon>edit_note</mat-icon><span i18n>Booking by hand</span></h3>
          <p i18n>
            You can book time for today, before {{ number('cutoffHour') }}, up to
            {{ number('manualMaxMinutes') | minutes }} in one entry. Another day has to be corrected
            by a parent.
          </p>
          <p class="aside" i18n>
            This is deliberate. Without it the weekly comparison would be pointless.
          </p>
        </mat-card-content></mat-card
      >

      <mat-card
        ><mat-card-content>
          <h3><mat-icon>healing</mat-icon><span i18n>Being ill</span></h3>
          <p i18n>
            If you are too ill for school, you are too ill for screens. Your body needs rest and a
            screen is not rest. Books, audio books and games at the table instead.
          </p>
        </mat-card-content></mat-card
      >

      <mat-card
        ><mat-card-content>
          <h3><mat-icon>forum</mat-icon><span i18n>What is open to discussion</span></h3>
          <p i18n>
            The hours, the evening limit and the fact that there are consequences are set by the
            parents.
          </p>
          <p i18n>
            How the hours are spread across the week, what happens when friends are over, what the
            consequence for a broken rule looks like and what gets watched on film night are all
            open.
          </p>
        </mat-card-content></mat-card
      >

      <mat-card
        ><mat-card-content>
          <h3><mat-icon>info</mat-icon><span i18n>What this app does</span></h3>
          <p i18n>
            It counts. It does not block anything and it does not watch what you do on a device. The
            limits on the devices themselves do that.
          </p>
        </mat-card-content></mat-card
      >
    </div>
  `,
})
export class RulesComponent {
  private readonly api = inject(ApiService);
  /**
   * The values in force this week and why. The child sees them and cannot
   * change them: there is not a single form control on this page.
   */
  protected readonly settings = signal<EffectiveSettings | null>(null);

  protected readonly reason = computed(() => {
    const week = this.settings();
    return week ? weekReason(week) : '';
  });

  protected readonly holidayDaysInTermWeek = computed(() => {
    const week = this.settings();
    return week !== null && week.scope === 'TERM' && week.holidayDayCount > 0;
  });

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      this.settings.set(await this.api.effectiveSettings());
    } catch {
      // the page still reads sensibly with the documented defaults
      this.settings.set(null);
    }
  }

  protected number(key: string): number {
    const raw = this.settings()?.values[key];
    return raw === undefined ? (FALLBACKS[key] ?? 0) : raw;
  }
}

/** The documented defaults, used until the real settings arrive. */
const FALLBACKS: Record<string, number> = {
  weeklyMinutes: 480,
  weekdayCapMinutes: 60,
  weekendCapMinutes: 120,
  quickDailyMinutes: 15,
  cutoffHour: 20,
  bonusMinutes: 60,
  bonusWeekendCapMinutes: 150,
  maxPenaltyMinutes: 120,
  toleranceMinutes: 10,
  manualMaxMinutes: 240,
  deliberatePenaltyMinutes: 60,
};

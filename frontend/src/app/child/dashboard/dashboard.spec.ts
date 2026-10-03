import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { Account } from '../../core/models';
import { DashboardComponent } from './dashboard';

/**
 * The child's screen. What is tested here is what the specification asks for
 * by name: the remaining week is the largest element, today and the quick
 * budget sit below it, the strip has seven days with today marked, and there
 * is no delete control anywhere on it.
 */
describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  /** The account arrives in a microtask, so the fixture has to settle first. */
  async function render(account: Account): Promise<void> {
    fixture = TestBed.createComponent(DashboardComponent);
    http.expectOne('/api/account/current').flush(account);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  it('shows the remaining week as the largest element on the screen', async () => {
    await render(account());

    const headline = (fixture.nativeElement as HTMLElement).querySelector('.headline .value');
    expect(headline?.textContent?.trim()).toBe('6 h 20 min');

    const tiles = (fixture.nativeElement as HTMLElement).querySelectorAll('.tile .n');
    expect(tiles).toHaveLength(2);
    expect(tiles[0].textContent?.trim()).toBe('40 min');
    expect(tiles[1].textContent?.trim()).toBe('15 min');
  });

  it('shows the budget it is counting down from', async () => {
    await render(account());
    expect(text()).toContain('of 8 h');
  });

  it('draws seven days with today marked', async () => {
    await render(account());

    const days = (fixture.nativeElement as HTMLElement).querySelectorAll('.day');
    expect(days).toHaveLength(7);

    const today = (fixture.nativeElement as HTMLElement).querySelectorAll('.day.today');
    expect(today).toHaveLength(1);
    expect(today[0].getAttribute('data-date')).toBe('2026-10-02');
  });

  it('never offers the child a way to delete an entry', async () => {
    await render(account());

    const html = (fixture.nativeElement as HTMLElement).innerHTML;
    expect(html).not.toContain('>delete<');
    expect(html).not.toContain('>edit<');
  });

  it('shows the arithmetic behind the number, not only the answer', async () => {
    await render(account());

    // budget, correction with its reason, used, left
    expect(text()).toContain('Budget');
    expect(text()).toContain('Used');
    expect(text()).toContain('bike left outside');
  });

  it('shows how long an entry was to the second', async () => {
    await render({
      ...account(),
      todayEntries: [{ ...account().todayEntries[0], seconds: 40 }],
    });
    expect(text()).toContain('40 s');
  });

  it('shows what is left to the second', async () => {
    await render({
      ...account(),
      balance: { ...account().balance, remainingTodaySeconds: 39 * 60 + 20 },
    });
    expect(text()).toContain('39 min 20 s');
  });

  it('shows a running session with a countdown and only a stop control', async () => {
    await render({
      ...account(),
      openSession: {
        id: 7,
        type: 'FUN',
        deviceId: 1,
        deviceName: 'iPad',
        startedAt: new Date(Date.now() - 5 * 60_000).toISOString(),
        elapsedSeconds: 5 * 60,
        // what was available before the session took anything
        countdownAgainstSeconds: 40 * 60,
      },
    });

    expect(text()).toContain('iPad');
    expect(text()).toContain('Stop');
    expect(text()).not.toContain('Start');
    const clock = (fixture.nativeElement as HTMLElement).querySelector('.clock');
    expect(clock?.textContent?.trim()).toMatch(/^0:3[45]:/);
  });

  it('says why nothing can be started once the cut off has passed', async () => {
    await render({ ...account(), screensOff: true });
    expect(text()).toContain('Nothing starts after 20:00');
  });

  it('explains the bonus rather than just showing a bigger number', async () => {
    await render({
      ...account(),
      bonusActive: true,
      balance: { ...account().balance, weeklyBudgetSeconds: 540 * 60 },
    });
    expect(text()).toContain('Last week matched');
    // the bonus in force, not a number written into the page
    expect(text()).toContain('extra 1 h');
  });

  it('says so plainly when the week is used up', async () => {
    await render({
      ...account(),
      balance: { ...account().balance, remainingWeekSeconds: 0, availableNowSeconds: 0 },
    });

    const headline = (fixture.nativeElement as HTMLElement).querySelector('.headline .value');
    expect(headline?.textContent?.trim()).toBe('0 min');
    expect(headline?.classList.contains('none')).toBe(true);
  });

  function account(): Account {
    return {
      displayName: 'A Child',
      today: '2026-10-02',
      weekStart: '2026-09-28',
      balance: {
        weeklyBudgetSeconds: 480 * 60,
        adjustmentSeconds: -30 * 60,
        weekUsedSeconds: 70 * 60,
        remainingWeekSeconds: 380 * 60,
        dailyCapSeconds: 60 * 60,
        dayUsedSeconds: 20 * 60,
        remainingTodaySeconds: 40 * 60,
        quickBudgetSeconds: 15 * 60,
        quickUsedSeconds: 0,
        remainingQuickSeconds: 15 * 60,
        availableNowSeconds: 40 * 60,
      },
      holidayWeek: false,
      bonusActive: false,
      cutoffHour: 20,
      rules: {
        weekStart: '2026-09-28',
        scope: 'TERM',
        holidayDayCount: 0,
        holidayWeekThresholdDays: 4,
        holidayDays: [],
        holidayPeriodNames: [],
        bonusActive: false,
        weeklyBudgetMinutes: 480,
        values: { weeklyMinutes: 480, bonusMinutes: 60, cutoffHour: 20 },
        days: [],
      },
      screensOff: false,
      week: [
        day('2026-09-28', 'MONDAY', 60, 50),
        day('2026-09-29', 'TUESDAY', 60, 0),
        day('2026-09-30', 'WEDNESDAY', 60, 0),
        day('2026-10-01', 'THURSDAY', 60, 0),
        { ...day('2026-10-02', 'FRIDAY', 60, 20), today: true },
        { ...day('2026-10-03', 'SATURDAY', 120, 0), future: true },
        { ...day('2026-10-04', 'SUNDAY', 120, 0), future: true },
      ],
      todayEntries: [
        {
          id: 1,
          day: '2026-10-02',
          type: 'FUN',
          source: 'MANUAL',
          deviceId: 1,
          deviceName: 'iPad',
          startedAt: '2026-10-02T14:00:00Z',
          endedAt: '2026-10-02T14:20:00Z',
          seconds: 20 * 60,
          running: false,
          autoClosed: false,
        },
      ],
      weekAdjustments: [
        {
          id: 1,
          weekStart: '2026-09-28',
          minutes: -30,
          reason: 'bike left outside',
          createdAt: '2026-09-30T18:00:00Z',
          fromWeeklyCheck: false,
        },
      ],
      devices: [
        { id: 1, name: 'iPad', active: true },
        { id: 2, name: 'TV', active: true },
      ],
    };
  }

  function day(
    date: string,
    dayOfWeek: Account['week'][number]['dayOfWeek'],
    cap: number,
    used: number,
  ): Account['week'][number] {
    return {
      date,
      dayOfWeek,
      capSeconds: cap * 60,
      usedSeconds: used * 60,
      remainingSeconds: Math.max(0, cap - used) * 60,
      quickUsedSeconds: 0,
      today: false,
      future: false,
    };
  }
});

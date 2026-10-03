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

  it('shows a running session with a countdown and only a stop control', async () => {
    await render({
      ...account(),
      openSession: {
        id: 7,
        type: 'FUN',
        deviceId: 1,
        deviceName: 'iPad',
        startedAt: new Date(Date.now() - 5 * 60_000).toISOString(),
        elapsedMinutes: 5,
        countdownAgainstMinutes: 40,
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
      balance: { ...account().balance, weeklyBudgetMinutes: 540 },
    });
    expect(text()).toContain('Last week matched');
  });

  it('says so plainly when the week is used up', async () => {
    await render({
      ...account(),
      balance: { ...account().balance, remainingWeekMinutes: 0, availableNowMinutes: 0 },
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
        weeklyBudgetMinutes: 480,
        adjustmentMinutes: -30,
        weekUsedMinutes: 70,
        remainingWeekMinutes: 380,
        dailyCapMinutes: 60,
        dayUsedMinutes: 20,
        remainingTodayMinutes: 40,
        quickBudgetMinutes: 15,
        quickUsedMinutes: 0,
        remainingQuickMinutes: 15,
        availableNowMinutes: 40,
      },
      holidayWeek: false,
      bonusActive: false,
      cutoffHour: 20,
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
          minutes: 20,
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
      capMinutes: cap,
      usedMinutes: used,
      remainingMinutes: Math.max(0, cap - used),
      quickUsedMinutes: 0,
      today: false,
      future: false,
    };
  }
});

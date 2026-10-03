import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { Week } from '../../core/models';
import { CheckComponent } from './check';

/**
 * The difference has to be right on the screen before anything is saved. If
 * the screen and the server disagree about what a check will cost, the
 * conversation on Sunday evening is over a surprise instead of a number.
 */
describe('CheckComponent', () => {
  let fixture: ComponentFixture<CheckComponent>;
  let component: CheckComponent;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(CheckComponent);
    component = fixture.componentInstance;

    http.expectOne((r) => r.url === '/api/account/week').flush(week(300));
    http.expectOne('/api/sessions/devices').flush([
      { id: 1, name: 'iPad', active: true },
      { id: 2, name: 'TV', active: true },
    ]);
    // the week's own values, not today's
    const effective = http.expectOne((r) => r.url === '/api/settings/effective');
    expect(effective.request.params.get('week')).toBe(component['weekStart']());
    effective.flush({
      weekStart: '2026-09-28',
      scope: 'TERM',
      holidayDayCount: 0,
      holidayWeekThresholdDays: 4,
      holidayDays: [],
      holidayPeriodNames: [],
      bonusActive: false,
      weeklyBudgetMinutes: 480,
      values: { toleranceMinutes: 10, maxPenaltyMinutes: 120, deliberatePenaltyMinutes: 60 },
      days: [],
    });
    fixture.detectChanges();
  });

  const inner = () =>
    component as unknown as {
      setReported: (id: number, m: number) => void;
      deliberate: { set: (v: boolean) => void };
      reportedTotal: () => number;
      difference: () => number;
      penalty: () => number;
      verdictKind: () => string;
    };

  it('adds the devices up as they are typed', () => {
    inner().setReported(1, 200);
    inner().setReported(2, 110);
    expect(inner().reportedTotal()).toBe(310);
    expect(inner().difference()).toBe(10);
  });

  it('calls a difference within tolerance a match', () => {
    inner().setReported(1, 305);
    expect(inner().verdictKind()).toBe('clean');
    expect(inner().penalty()).toBe(0);
  });

  it('treats the tolerance itself as a match, in both directions', () => {
    inner().setReported(1, 310);
    expect(inner().verdictKind()).toBe('clean');
    inner().setReported(1, 290);
    expect(inner().verdictKind()).toBe('clean');
  });

  it('deducts the whole difference once it is past tolerance', () => {
    inner().setReported(1, 311);
    expect(inner().verdictKind()).toBe('penalty');
    expect(inner().penalty()).toBe(11);
  });

  it('never promises a deduction larger than the cap', () => {
    inner().setReported(1, 900);
    expect(inner().difference()).toBe(600);
    expect(inner().penalty()).toBe(120);
  });

  it('adds the extra hour for a deliberate week before the cap', () => {
    inner().setReported(1, 330);
    inner().deliberate.set(true);
    expect(inner().penalty()).toBe(90);

    inner().setReported(1, 400);
    expect(inner().penalty()).toBe(120);
  });

  it('never calls a deliberate week clean, even when the numbers match', () => {
    inner().setReported(1, 300);
    inner().deliberate.set(true);
    expect(inner().verdictKind()).toBe('penalty');
    expect(inner().penalty()).toBe(60);
  });

  it('compares the log to the nearest minute, the same way the server does', () => {
    // 300 min 40 s logged: the devices would say 301
    component['week'].set(week(300, 40));
    inner().setReported(1, 301);
    expect(inner().difference()).toBe(0);

    // 300 min 29 s rounds down
    component['week'].set(week(300, 29));
    expect(inner().difference()).toBe(1);
  });

  it('flags more booked than the devices saw without deducting anything', () => {
    inner().setReported(1, 200);
    expect(inner().verdictKind()).toBe('look');
    expect(inner().penalty()).toBe(0);
  });

  function week(usedMinutes: number, extraSeconds = 0): Week {
    const used = usedMinutes * 60 + extraSeconds;
    return {
      weekStart: '2026-09-28',
      balance: {
        weeklyBudgetSeconds: 480 * 60,
        adjustmentSeconds: 0,
        weekUsedSeconds: used,
        remainingWeekSeconds: 480 * 60 - used,
        dailyCapSeconds: 60 * 60,
        dayUsedSeconds: 0,
        remainingTodaySeconds: 60 * 60,
        quickBudgetSeconds: 15 * 60,
        quickUsedSeconds: 0,
        remainingQuickSeconds: 15 * 60,
        availableNowSeconds: 60 * 60,
      },
      holidayWeek: false,
      bonusActive: false,
      days: [],
      adjustments: [],
    };
  }
});

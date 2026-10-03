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
    http.expectOne('/api/settings').flush({
      toleranceMinutes: '10',
      maxPenaltyMinutes: '120',
      deliberatePenaltyMinutes: '60',
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

  it('flags more booked than the devices saw without deducting anything', () => {
    inner().setReported(1, 200);
    expect(inner().verdictKind()).toBe('look');
    expect(inner().penalty()).toBe(0);
  });

  function week(usedMinutes: number): Week {
    return {
      weekStart: '2026-09-28',
      balance: {
        weeklyBudgetMinutes: 480,
        adjustmentMinutes: 0,
        weekUsedMinutes: usedMinutes,
        remainingWeekMinutes: 480 - usedMinutes,
        dailyCapMinutes: 60,
        dayUsedMinutes: 0,
        remainingTodayMinutes: 60,
        quickBudgetMinutes: 15,
        quickUsedMinutes: 0,
        remainingQuickMinutes: 15,
        availableNowMinutes: 60,
      },
      holidayWeek: false,
      bonusActive: false,
      days: [],
      adjustments: [],
    };
  }
});

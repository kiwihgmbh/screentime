import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { DayOfWeek, EffectiveSettings } from '../../core/models';
import { RulesComponent } from './rules';

/** The child sees the rules of this week and why, and cannot change any of it. */
describe('RulesComponent', () => {
  let fixture: ComponentFixture<RulesComponent>;
  let http: HttpTestingController;

  const dates = [
    '2026-09-28',
    '2026-09-29',
    '2026-09-30',
    '2026-10-01',
    '2026-10-02',
    '2026-10-03',
    '2026-10-04',
  ];
  const names: DayOfWeek[] = [
    'MONDAY',
    'TUESDAY',
    'WEDNESDAY',
    'THURSDAY',
    'FRIDAY',
    'SATURDAY',
    'SUNDAY',
  ];
  const holidayWeek: EffectiveSettings = {
    weekStart: dates[0],
    scope: 'HOLIDAY',
    holidayDayCount: 4,
    holidayWeekThresholdDays: 4,
    holidayDays: dates.slice(3),
    holidayPeriodName: 'Autumn holidays',
    holidayPeriodNames: ['Autumn holidays'],
    bonusActive: false,
    weeklyBudgetMinutes: 720,
    values: {
      weeklyMinutes: 720,
      weekdayCapMinutes: 120,
      weekendCapMinutes: 120,
      quickDailyMinutes: 15,
      cutoffHour: 21,
      bonusMinutes: 60,
      bonusWeekendCapMinutes: 150,
      maxPenaltyMinutes: 120,
      toleranceMinutes: 10,
      manualMaxMinutes: 240,
      deliberatePenaltyMinutes: 60,
    },
    days: dates.map((date, i) => ({ date, dayOfWeek: names[i], holiday: i >= 3, capMinutes: 120 })),
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RulesComponent);
    http.expectOne((r) => r.url === '/api/settings/effective').flush(holidayWeek);
    await fixture.whenStable();
    fixture.detectChanges();
  });

  const root = () => fixture.nativeElement as HTMLElement;

  it('says why the numbers are what they are', () => {
    expect(root().textContent).toContain('Holiday rules this week');
    expect(root().querySelector('[data-testid="reason"]')?.textContent).toBe(
      'This week counts as holiday: 4 of 7 days are in Autumn holidays.',
    );
  });

  it('shows the values in force this week', () => {
    expect(root().textContent).toContain('12 h a week');
    expect(root().textContent).toContain('Nothing starts after 21');
  });

  it('has no form controls at all', () => {
    expect(
      root().querySelectorAll('input, select, textarea, button, mat-radio-group, mat-checkbox'),
    ).toHaveLength(0);
  });

  it('never asks for the parents’ overview', () => {
    http.expectNone('/api/settings');
  });
});

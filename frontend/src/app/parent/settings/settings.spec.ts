import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { DayOfWeek, EffectiveSettings, Settings, SettingsOverview } from '../../core/models';
import { SettingsComponent } from './settings';

describe('SettingsComponent', () => {
  let fixture: ComponentFixture<SettingsComponent>;
  let http: HttpTestingController;

  const term: Settings = {
    weeklyMinutes: '480',
    weekdayCapMinutes: '60',
    weekendCapMinutes: '120',
    quickDailyMinutes: '15',
    cutoffHour: '20',
    bonusMinutes: '60',
    bonusWeekendCapMinutes: '150',
    maxPenaltyMinutes: '120',
    toleranceMinutes: '10',
    manualMaxMinutes: '240',
    deliberatePenaltyMinutes: '60',
  };
  const holiday: Settings = {
    ...term,
    weeklyMinutes: '720',
    weekdayCapMinutes: '120',
    cutoffHour: '21',
  };
  const general: Settings = { holidayWeekThresholdDays: '4' };
  const scope = (name: 'TERM' | 'HOLIDAY' | 'GLOBAL', values: Settings) => ({
    scope: name,
    thisWeek: values,
    nextWeek: values,
    history: [],
  });

  const overview: SettingsOverview = {
    thisWeek: '2026-09-28',
    nextWeek: '2026-10-05',
    term: scope('TERM', term),
    holiday: scope('HOLIDAY', holiday),
    general: scope('GLOBAL', general),
    changes: [
      {
        at: '2026-10-02T18:00:00Z',
        by: 'A Parent',
        kind: 'SETTING',
        action: 'CHANGE',
        scope: 'TERM',
        key: 'weeklyMinutes',
        from: '480',
        to: '420',
        validFrom: '2026-10-05',
      },
      {
        at: '2026-10-01T18:00:00Z',
        by: 'A Parent',
        kind: 'HOLIDAY_PERIOD',
        action: 'CREATE',
        key: 'Autumn holidays',
        to: 'Autumn holidays, 2026-10-01 to 2026-10-18',
      },
    ],
  };

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
  const effective: EffectiveSettings = {
    weekStart: dates[0],
    scope: 'HOLIDAY',
    holidayDayCount: 4,
    holidayWeekThresholdDays: 4,
    holidayDays: dates.slice(3),
    holidayPeriodName: 'Autumn holidays',
    holidayPeriodNames: ['Autumn holidays'],
    bonusActive: false,
    weeklyBudgetMinutes: 720,
    values: { weeklyMinutes: 720, weekdayCapMinutes: 120, weekendCapMinutes: 120, cutoffHour: 21 },
    days: dates.map((date, i) => ({ date, dayOfWeek: names[i], holiday: i >= 3, capMinutes: 120 })),
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SettingsComponent);
    http.expectOne('/api/settings').flush(overview);
    http.expectOne((r) => r.url === '/api/settings/effective').flush(effective);
    await fixture.whenStable();
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/holidays').flush([]);
    await fixture.whenStable();
    fixture.detectChanges();
  });

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  it('says what the current week runs on above the two value sets', () => {
    const summary = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="week-summary"] span',
    );
    expect(summary?.textContent?.trim()).toBe(
      'This week counts as holiday: 4 of 7 days are in Autumn holidays. ' +
        'Budget 12 h, 2 h per day, screens off at 21:00.',
    );
    // the sentence first, then the sections in the order the task names them
    const root = fixture.nativeElement as HTMLElement;
    expect(
      root.querySelector('mat-card')?.querySelector('[data-testid="week-summary"]'),
    ).toBeTruthy();
    const headings = Array.from(root.querySelectorAll('h3')).map((h) => h.textContent?.trim());
    expect(headings).toEqual(['Term values', 'Holiday values', 'Holiday periods', 'Changes']);
  });

  it('shows the change log in words, from which value to which', () => {
    expect(text()).toContain('Term: Weekly budget 8 h → 7 h, from');
    expect(text()).toContain('Holiday period added: Autumn holidays, 2026-10-01 to 2026-10-18');
    expect(text()).toContain('A Parent');
  });
});

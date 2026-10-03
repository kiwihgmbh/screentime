import { describe, expect, it } from 'vitest';
import { DayOfWeek, EffectiveSettings } from './models';
import { describeWeek, holidayWarning, weekReason } from './week-summary';

const DAYS: DayOfWeek[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];
// Monday 28 September to Sunday 4 October 2026
const DATES = [
  '2026-09-28',
  '2026-09-29',
  '2026-09-30',
  '2026-10-01',
  '2026-10-02',
  '2026-10-03',
  '2026-10-04',
];

const TERM = {
  weeklyMinutes: 480,
  weekdayCapMinutes: 60,
  weekendCapMinutes: 120,
  cutoffHour: 20,
  bonusMinutes: 60,
  bonusWeekendCapMinutes: 150,
};
const HOLIDAY = { ...TERM, weeklyMinutes: 720, weekdayCapMinutes: 120, cutoffHour: 21 };

function week(
  scope: 'TERM' | 'HOLIDAY',
  holidayDates: string[],
  names: string[],
  caps: Partial<Record<string, number>> = {},
  bonus = false,
): EffectiveSettings {
  const values = scope === 'HOLIDAY' ? HOLIDAY : TERM;
  return {
    weekStart: DATES[0],
    scope,
    holidayDayCount: holidayDates.length,
    holidayWeekThresholdDays: 4,
    holidayDays: holidayDates,
    holidayPeriodName: names[0],
    holidayPeriodNames: names,
    bonusActive: bonus,
    weeklyBudgetMinutes: values.weeklyMinutes + (bonus ? values.bonusMinutes : 0),
    values,
    days: DATES.map((date, i) => ({
      date,
      dayOfWeek: DAYS[i],
      holiday: holidayDates.includes(date),
      capMinutes: caps[date] ?? (i < 5 ? values.weekdayCapMinutes : values.weekendCapMinutes),
    })),
  };
}

describe('describeWeek', () => {
  it('says a holiday week in one sentence, with the numbers', () => {
    const text = describeWeek(week('HOLIDAY', DATES.slice(3), ['Autumn holidays']));
    expect(text).toBe(
      'This week counts as holiday: 4 of 7 days are in Autumn holidays. ' +
        'Budget 12 h, 2 h per day, screens off at 21:00.',
    );
  });

  it('says a plain term week', () => {
    expect(describeWeek(week('TERM', [], []))).toBe(
      'This week runs on term values. ' +
        'Budget 8 h, 1 h on school days and 2 h at the weekend, screens off at 20:00.',
    );
  });

  it('explains holiday days in a term week and their ceiling', () => {
    const text = describeWeek(
      week('TERM', DATES.slice(5), ['Autumn holidays'], { '2026-10-03': 130, '2026-10-04': 130 }),
    );
    expect(text).toContain(
      '2 of 7 days are in Autumn holidays, fewer than the 4 that make a holiday week',
    );
    expect(text).toContain(
      'Saturday and Sunday are holiday days, with a ceiling of 2 h 10 min each.',
    );
  });

  it('lists different holiday ceilings day by day', () => {
    const text = weekReason(
      week('TERM', DATES.slice(4), ['Autumn holidays'], {
        '2026-10-02': 120,
        '2026-10-03': 130,
        '2026-10-04': 130,
      }),
    );
    expect(text).toContain(
      'Friday, Saturday and Sunday are holiday days, with ceilings of 2 h, 2 h 10 min and 2 h 10 min.',
    );
  });

  it('names a single holiday day in the singular', () => {
    expect(weekReason(week('TERM', ['2026-09-30'], ['Teacher training']))).toContain(
      'Wednesday is a holiday day, with a ceiling of 1 h.',
    );
  });

  it('names two periods in one week', () => {
    expect(
      weekReason(
        week(
          'HOLIDAY',
          [DATES[0], DATES[1], DATES[5], DATES[6]],
          ['Long weekend', 'Autumn holidays'],
        ),
      ),
    ).toBe('This week counts as holiday: 4 of 7 days are in Long weekend and Autumn holidays.');
  });

  it('includes the bonus in the budget and the weekend ceiling', () => {
    expect(describeWeek(week('TERM', [], [], {}, true))).toContain(
      'Budget 9 h with the bonus, 1 h on school days and 2 h 30 min at the weekend',
    );
  });
});

describe('holidayWarning', () => {
  const term = week('TERM', [], []);

  it('says nothing for a period outside the current week', () => {
    expect(
      holidayWarning(term, HOLIDAY, { startDate: '2026-10-10', endDate: '2026-10-25' }),
    ).toBeNull();
  });

  it('names the days that change when a period starts inside the current week', () => {
    const text = holidayWarning(term, HOLIDAY, { startDate: '2026-10-03', endDate: '2026-10-18' });
    expect(text).toContain('Saturday and Sunday become holiday days');
    expect(text).toContain('stays on term values (2 of 7)');
  });

  it('says when the whole week switches to holiday values, with what that means', () => {
    const text = holidayWarning(term, HOLIDAY, { startDate: '2026-10-01', endDate: '2026-10-18' });
    expect(text).toContain('Thursday, Friday, Saturday and Sunday become holiday days');
    expect(text).toContain(
      '4 of 7, so the whole week switches to holiday values: budget 12 h, screens off at 21:00',
    );
  });

  it('counts a period that started before the week', () => {
    const text = holidayWarning(term, HOLIDAY, { startDate: '2026-09-20', endDate: '2026-09-29' });
    expect(text).toContain('Monday and Tuesday become holiday days');
  });

  it('when a period is edited, compares with what it covered before', () => {
    const current = week('HOLIDAY', DATES.slice(3), ['Autumn holidays']);
    const text = holidayWarning(
      current,
      HOLIDAY,
      { startDate: '2026-10-02', endDate: '2026-10-18' },
      { startDate: '2026-10-01', endDate: '2026-10-18' },
    );
    expect(text).toContain('Thursday is no longer a holiday day');
    expect(text).toContain('3 of 7, so the week goes back to term values');
  });

  it('says nothing when an edit does not touch the current week', () => {
    const current = week('HOLIDAY', DATES.slice(3), ['Autumn holidays']);
    expect(
      holidayWarning(
        current,
        HOLIDAY,
        { startDate: '2026-10-01', endDate: '2026-10-20' },
        { startDate: '2026-10-01', endDate: '2026-10-18' },
      ),
    ).toBeNull();
  });

  it('ignores a period that is not complete yet', () => {
    expect(holidayWarning(term, HOLIDAY, { startDate: '2026-10-03', endDate: '' })).toBeNull();
    expect(
      holidayWarning(term, HOLIDAY, { startDate: '2026-10-05', endDate: '2026-10-01' }),
    ).toBeNull();
  });
});

import { describe, expect, it } from 'vitest';
import { RULE_TEXT, SETTING_LABELS, validateThreshold, validateValues } from './settings-rules';

/**
 * The same rules the server checks, so the form can say what is wrong before
 * anything is sent. The server stays the authority; these only have to agree
 * with it, which is why the rule names and the numbers in the messages are
 * the same.
 */
describe('validateValues', () => {
  const term: Record<string, string> = {
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
  const holiday = {
    ...term,
    weeklyMinutes: '720',
    weekdayCapMinutes: '120',
    cutoffHour: '21',
  };
  const rules = (values: Record<string, string>) => validateValues(values).map((p) => p.rule);

  it('accepts both sets of defaults', () => {
    expect(validateValues(term)).toEqual([]);
    expect(validateValues(holiday)).toEqual([]);
  });

  it('wants the daily ceilings to add up to more than the week, and names the numbers', () => {
    const problems = validateValues({
      ...term,
      weekendCapMinutes: '30',
      bonusWeekendCapMinutes: '200',
    });
    expect(problems).toHaveLength(1);
    expect(problems[0].rule).toBe('DAILY_CEILINGS_EXCEED_WEEK');
    expect(problems[0].message).toContain('360');
    expect(problems[0].message).toContain('480');
    expect(problems[0].keys).toEqual(['weeklyMinutes', 'weekdayCapMinutes', 'weekendCapMinutes']);
  });

  it('treats equal ceilings as a broken rule and one minute more as fine', () => {
    expect(rules({ ...term, weeklyMinutes: '540', bonusMinutes: '0' })).toContain(
      'DAILY_CEILINGS_EXCEED_WEEK',
    );
    expect(rules({ ...term, weeklyMinutes: '539', bonusMinutes: '0' })).toEqual([]);
  });

  it('wants a bonus week to bind as well', () => {
    // 500 + 60 = 560 against 5 x 60 + 2 x 120 = 540
    const problems = validateValues({
      ...term,
      weeklyMinutes: '500',
      bonusWeekendCapMinutes: '120',
    });
    expect(problems.map((p) => p.rule)).toEqual(['BONUS_CEILINGS_EXCEED_BONUS_WEEK']);
    expect(problems[0].message).toContain('560');
    expect(problems[0].message).toContain('540');
  });

  it('does not let the bonus weekend ceiling fall below the ordinary one', () => {
    const problems = validateValues({
      ...term,
      weekdayCapMinutes: '100',
      bonusWeekendCapMinutes: '110',
    });
    expect(problems.map((p) => p.rule)).toEqual(['BONUS_WEEKEND_CAP_AT_LEAST_WEEKEND_CAP']);
    expect(problems[0].keys).toEqual(['bonusWeekendCapMinutes', 'weekendCapMinutes']);
  });

  it('keeps the cut off between noon and eleven', () => {
    expect(rules({ ...term, cutoffHour: '12' })).toEqual([]);
    expect(rules({ ...term, cutoffHour: '23' })).toEqual([]);
    expect(rules({ ...term, cutoffHour: '11' })).toEqual(['CUTOFF_HOUR_RANGE']);
    expect(rules({ ...term, cutoffHour: '24' })).toEqual(['CUTOFF_HOUR_RANGE']);
  });

  it('refuses a negative number of minutes and names the field', () => {
    const problems = validateValues({ ...term, toleranceMinutes: '-1' });
    expect(problems.map((p) => p.rule)).toEqual(['NOT_NEGATIVE']);
    expect(problems[0].keys).toEqual(['toleranceMinutes']);
  });

  it('refuses a week longer than a week', () => {
    expect(rules({ ...term, weeklyMinutes: '10081' })).toContain('WEEKLY_MAXIMUM');
    expect(rules({ ...term, weeklyMinutes: '10080' })).not.toContain('WEEKLY_MAXIMUM');
  });

  it('calls an empty or non numeric field what it is instead of doing arithmetic with it', () => {
    const problems = validateValues({ ...term, weeklyMinutes: '' });
    expect(problems.map((p) => p.rule)).toEqual(['NOT_A_NUMBER']);
    expect(problems[0].keys).toEqual(['weeklyMinutes']);
    expect(rules({ ...term, cutoffHour: '20.5' })).toEqual(['NOT_A_NUMBER']);
  });

  it('reports every broken rule at once', () => {
    expect(
      rules({ ...term, cutoffHour: '8', toleranceMinutes: '-3', weekendCapMinutes: '30' }),
    ).toEqual(
      expect.arrayContaining(['CUTOFF_HOUR_RANGE', 'NOT_NEGATIVE', 'DAILY_CEILINGS_EXCEED_WEEK']),
    );
  });
});

describe('validateThreshold', () => {
  it('is a number of days from one to seven', () => {
    expect(validateThreshold('1')).toEqual([]);
    expect(validateThreshold('7')).toEqual([]);
    expect(validateThreshold('0').map((p) => p.rule)).toEqual(['HOLIDAY_WEEK_THRESHOLD_RANGE']);
    expect(validateThreshold('8').map((p) => p.rule)).toEqual(['HOLIDAY_WEEK_THRESHOLD_RANGE']);
    expect(validateThreshold('').map((p) => p.rule)).toEqual(['NOT_A_NUMBER']);
  });
});

describe('the text next to each field', () => {
  it('has a label and a one line rule for every setting', () => {
    for (const key of [
      'weeklyMinutes',
      'weekdayCapMinutes',
      'weekendCapMinutes',
      'quickDailyMinutes',
      'cutoffHour',
      'bonusMinutes',
      'bonusWeekendCapMinutes',
      'maxPenaltyMinutes',
      'toleranceMinutes',
      'manualMaxMinutes',
      'deliberatePenaltyMinutes',
      'holidayWeekThresholdDays',
    ]) {
      expect(SETTING_LABELS[key]?.name, key).toBeTruthy();
      expect(RULE_TEXT[key], key).toBeTruthy();
    }
  });
});

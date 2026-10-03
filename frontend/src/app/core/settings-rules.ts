/**
 * The rules a set of values has to keep, the same ones the server checks in
 * SettingsValidator, so the form can mark a field before anything is sent.
 * The server stays the authority: it also checks the weeks a change reaches
 * after the first, which this cannot see.
 *
 * Every message names the actual numbers. "The ceilings are too low" helps
 * nobody; "540 is not more than 540" does.
 */

export type SettingsRule =
  | 'DAILY_CEILINGS_EXCEED_WEEK'
  | 'BONUS_CEILINGS_EXCEED_BONUS_WEEK'
  | 'BONUS_WEEKEND_CAP_AT_LEAST_WEEKEND_CAP'
  | 'CUTOFF_HOUR_RANGE'
  | 'NOT_NEGATIVE'
  | 'WEEKLY_MAXIMUM'
  | 'HOLIDAY_WEEK_THRESHOLD_RANGE'
  | 'NOT_A_NUMBER';

export interface SettingsProblem {
  rule: SettingsRule;
  /** the fields the rule concerns; the message is shown under each of them */
  keys: string[];
  message: string;
}

export const THRESHOLD_KEY = 'holidayWeekThresholdDays';

/** The rule values, in the order the form shows them. */
export const VALUE_KEYS = [
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
] as const;

const MINUTE_KEYS = VALUE_KEYS.filter((key) => key !== 'cutoffHour');
const MINUTES_IN_A_WEEK = 7 * 24 * 60;

export function validateValues(
  values: Record<string, string | number | undefined>,
): SettingsProblem[] {
  const problems: SettingsProblem[] = [];
  const n: Record<string, number> = {};
  for (const key of VALUE_KEYS) {
    const value = whole(values[key]);
    if (value === null) {
      problems.push({
        rule: 'NOT_A_NUMBER',
        keys: [key],
        message: $localize`Enter a whole number.`,
      });
    } else {
      n[key] = value;
    }
  }
  // arithmetic with a missing number would only produce confusing messages
  if (problems.length > 0) {
    return problems;
  }

  const ceilings = 5 * n['weekdayCapMinutes'] + 2 * n['weekendCapMinutes'];
  if (ceilings <= n['weeklyMinutes']) {
    problems.push({
      rule: 'DAILY_CEILINGS_EXCEED_WEEK',
      keys: ['weeklyMinutes', 'weekdayCapMinutes', 'weekendCapMinutes'],
      message: $localize`The daily ceilings add up to 5 × ${n['weekdayCapMinutes']} + 2 × ${n['weekendCapMinutes']} = ${ceilings} minutes, not more than the weekly budget of ${n['weeklyMinutes']}. The weekly budget would never bind.`,
    });
  }

  const bonusWeek = n['weeklyMinutes'] + n['bonusMinutes'];
  const bonusCeilings = 5 * n['weekdayCapMinutes'] + 2 * n['bonusWeekendCapMinutes'];
  if (bonusWeek >= bonusCeilings) {
    problems.push({
      rule: 'BONUS_CEILINGS_EXCEED_BONUS_WEEK',
      keys: ['weeklyMinutes', 'bonusMinutes', 'weekdayCapMinutes', 'bonusWeekendCapMinutes'],
      message: $localize`In a bonus week the budget is ${n['weeklyMinutes']} + ${n['bonusMinutes']} = ${bonusWeek} minutes, not below the ceilings of 5 × ${n['weekdayCapMinutes']} + 2 × ${n['bonusWeekendCapMinutes']} = ${bonusCeilings}.`,
    });
  }

  if (n['bonusWeekendCapMinutes'] < n['weekendCapMinutes']) {
    problems.push({
      rule: 'BONUS_WEEKEND_CAP_AT_LEAST_WEEKEND_CAP',
      keys: ['bonusWeekendCapMinutes', 'weekendCapMinutes'],
      message: $localize`The weekend ceiling in a bonus week is ${n['bonusWeekendCapMinutes']} minutes, lower than the ordinary ${n['weekendCapMinutes']}. A bonus cannot take time away.`,
    });
  }

  if (n['cutoffHour'] < 12 || n['cutoffHour'] > 23) {
    problems.push({
      rule: 'CUTOFF_HOUR_RANGE',
      keys: ['cutoffHour'],
      message: $localize`The cut off has to be between 12 and 23, not ${n['cutoffHour']}.`,
    });
  }

  for (const key of MINUTE_KEYS) {
    if (n[key] < 0) {
      problems.push({
        rule: 'NOT_NEGATIVE',
        keys: [key],
        message: $localize`This cannot be negative, but is ${n[key]}.`,
      });
    }
  }

  if (n['weeklyMinutes'] > MINUTES_IN_A_WEEK) {
    problems.push({
      rule: 'WEEKLY_MAXIMUM',
      keys: ['weeklyMinutes'],
      message: $localize`${n['weeklyMinutes']} minutes is more than the ${MINUTES_IN_A_WEEK} a week has.`,
    });
  }

  return problems;
}

export function validateThreshold(value: string | number | undefined): SettingsProblem[] {
  const days = whole(value);
  if (days === null) {
    return [
      { rule: 'NOT_A_NUMBER', keys: [THRESHOLD_KEY], message: $localize`Enter a whole number.` },
    ];
  }
  if (days < 1 || days > 7) {
    return [
      {
        rule: 'HOLIDAY_WEEK_THRESHOLD_RANGE',
        keys: [THRESHOLD_KEY],
        message: $localize`A week has 1 to 7 days, not ${days}.`,
      },
    ];
  }
  return [];
}

function whole(value: string | number | undefined): number | null {
  if (value === undefined || value === null) {
    return null;
  }
  const text = String(value).trim();
  return /^-?\d+$/.test(text) ? Number(text) : null;
}

export const SETTING_LABELS: Record<string, { name: string; unit: string }> = {
  weeklyMinutes: { name: $localize`Weekly budget`, unit: $localize`min` },
  weekdayCapMinutes: { name: $localize`School day ceiling`, unit: $localize`min` },
  weekendCapMinutes: { name: $localize`Weekend ceiling`, unit: $localize`min` },
  quickDailyMinutes: { name: $localize`Looking things up, per day`, unit: $localize`min` },
  cutoffHour: { name: $localize`Screens off at`, unit: $localize`h` },
  bonusMinutes: { name: $localize`Bonus after a clean week`, unit: $localize`min` },
  bonusWeekendCapMinutes: { name: $localize`Weekend ceiling with bonus`, unit: $localize`min` },
  maxPenaltyMinutes: { name: $localize`Largest deduction per check`, unit: $localize`min` },
  toleranceMinutes: { name: $localize`Tolerance`, unit: $localize`min` },
  manualMaxMinutes: { name: $localize`Largest single entry by hand`, unit: $localize`min` },
  deliberatePenaltyMinutes: {
    name: $localize`Extra for working around the rules`,
    unit: $localize`min`,
  },
  holidayWeekThresholdDays: {
    name: $localize`Holiday days that make a holiday week`,
    unit: $localize`days`,
  },
};

/** The rule each field has to keep, in one line, shown under it. */
export const RULE_TEXT: Record<string, string> = {
  weeklyMinutes: $localize`Below 5 × school day + 2 × weekend ceiling, and at most 10080.`,
  weekdayCapMinutes: $localize`5 × this + 2 × weekend ceiling must be more than the weekly budget.`,
  weekendCapMinutes: $localize`5 × school day + 2 × this must be more than the weekly budget; at most the bonus weekend ceiling.`,
  quickDailyMinutes: $localize`0 or more. Outside the weekly budget.`,
  cutoffHour: $localize`An hour from 12 to 23. Nothing but the film starts at or after it.`,
  bonusMinutes: $localize`Weekly budget + this must stay below 5 × school day + 2 × bonus weekend ceiling.`,
  bonusWeekendCapMinutes: $localize`At least the weekend ceiling, so the bonus can be used.`,
  maxPenaltyMinutes: $localize`0 or more. No check takes more than this.`,
  toleranceMinutes: $localize`0 or more. A difference this small still counts as a match.`,
  manualMaxMinutes: $localize`0 or more. 0 means the child cannot book by hand.`,
  deliberatePenaltyMinutes: $localize`0 or more. Added before the largest deduction applies.`,
  holidayWeekThresholdDays: $localize`1 to 7. With fewer, only the holiday days get the holiday ceiling.`,
};

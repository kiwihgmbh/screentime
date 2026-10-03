import { ChecklistItemInput, DayOfWeek } from './models';
import { list } from './week-summary';

/**
 * How a checklist item's schedule reads in the parents' list: "Saturdays,
 * until 19 Dec", "School days", "Only on 9 Oct". The rules themselves are on
 * the server; this only says them in words.
 */

export const WEEK: DayOfWeek[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

const PLURAL: Record<DayOfWeek, string> = {
  MONDAY: $localize`Mondays`,
  TUESDAY: $localize`Tuesdays`,
  WEDNESDAY: $localize`Wednesdays`,
  THURSDAY: $localize`Thursdays`,
  FRIDAY: $localize`Fridays`,
  SATURDAY: $localize`Saturdays`,
  SUNDAY: $localize`Sundays`,
};

/** Two letters per day, for the toggles. */
export const SHORT: Record<DayOfWeek, string> = {
  MONDAY: $localize`Mo`,
  TUESDAY: $localize`Tu`,
  WEDNESDAY: $localize`We`,
  THURSDAY: $localize`Th`,
  FRIDAY: $localize`Fr`,
  SATURDAY: $localize`Sa`,
  SUNDAY: $localize`Su`,
};

const MONTHS = [
  $localize`Jan`,
  $localize`Feb`,
  $localize`Mar`,
  $localize`Apr`,
  $localize`May`,
  $localize`Jun`,
  $localize`Jul`,
  $localize`Aug`,
  $localize`Sep`,
  $localize`Oct`,
  $localize`Nov`,
  $localize`Dec`,
];

export const MAX_TEXT = 200;

type Schedule = Pick<ChecklistItemInput, 'weekdays' | 'validFrom' | 'validUntil'>;

export function describeSchedule(item: Schedule, thisYear = new Date().getFullYear()): string {
  const day = (date: string) => {
    const [y, m, d] = date.split('-').map(Number);
    return y === thisYear ? `${d} ${MONTHS[m - 1]}` : `${d} ${MONTHS[m - 1]} ${y}`;
  };

  if (item.validFrom && item.validUntil && item.validFrom === item.validUntil) {
    return $localize`Only on ${day(item.validFrom)}`;
  }

  const days = WEEK.filter((d) => item.weekdays.includes(d));
  let when: string;
  if (days.length === 0 || days.length === 7) {
    when = $localize`Every day`;
  } else if (days.join() === WEEK.slice(0, 5).join()) {
    when = $localize`School days`;
  } else if (days.join() === WEEK.slice(5).join()) {
    when = $localize`Weekends`;
  } else {
    when = list(days.map((d) => PLURAL[d]));
  }

  if (item.validFrom && item.validUntil) {
    return `${when}, ${$localize`${day(item.validFrom)} to ${day(item.validUntil)}`}`;
  }
  if (item.validFrom) {
    return `${when}, ${$localize`from ${day(item.validFrom)}`}`;
  }
  if (item.validUntil) {
    return `${when}, ${$localize`until ${day(item.validUntil)}`}`;
  }
  return when;
}

/** What is wrong with an item before it is sent, the same checks the server makes. */
export function checklistItemProblem(item: ChecklistItemInput): string | null {
  const text = item.text.trim();
  if (text.length === 0) {
    return $localize`Write what to remember.`;
  }
  if (text.length > MAX_TEXT) {
    return $localize`At most ${MAX_TEXT} characters, this has ${text.length}.`;
  }
  if (item.validFrom && item.validUntil && item.validUntil < item.validFrom) {
    return $localize`The last day is before the first day.`;
  }
  return null;
}

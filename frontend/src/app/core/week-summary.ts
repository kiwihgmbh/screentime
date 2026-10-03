import { DayOfWeek, EffectiveSettings } from './models';
import { MinutesPipe } from './minutes.pipe';

/**
 * The sentences that explain a week: why it runs on the values it runs on,
 * and what a holiday period about to be saved would change about it. Shared
 * by the parent's settings page and the child's rules page, so both say the
 * same thing in the same words.
 */

const minutes = new MinutesPipe();

const DAY_NAMES: Record<DayOfWeek, string> = {
  MONDAY: $localize`Monday`,
  TUESDAY: $localize`Tuesday`,
  WEDNESDAY: $localize`Wednesday`,
  THURSDAY: $localize`Thursday`,
  FRIDAY: $localize`Friday`,
  SATURDAY: $localize`Saturday`,
  SUNDAY: $localize`Sunday`,
};

/** "a", "a and b", "a, b and c" */
export function list(items: string[]): string {
  if (items.length <= 1) {
    return items.join('');
  }
  return `${items.slice(0, -1).join(', ')} ${$localize`and`} ${items[items.length - 1]}`;
}

/** Why the week runs on the values it runs on, without the numbers. */
export function weekReason(week: EffectiveSettings): string {
  const count = week.holidayDayCount;
  const names = list(week.holidayPeriodNames);

  if (week.scope === 'HOLIDAY') {
    return $localize`This week counts as holiday: ${count} of 7 days are in ${names}.`;
  }
  if (count === 0) {
    return $localize`This week runs on term values.`;
  }

  const holidayDays = week.days.filter((d) => d.holiday);
  const dayNames = list(holidayDays.map((d) => DAY_NAMES[d.dayOfWeek]));
  const caps = holidayDays.map((d) => d.capMinutes);
  const allSame = caps.every((c) => c === caps[0]);

  let ceilings: string;
  if (holidayDays.length === 1) {
    ceilings = $localize`${dayNames} is a holiday day, with a ceiling of ${minutes.transform(caps[0])}.`;
  } else if (allSame) {
    ceilings = $localize`${dayNames} are holiday days, with a ceiling of ${minutes.transform(caps[0])} each.`;
  } else {
    ceilings = $localize`${dayNames} are holiday days, with ceilings of ${list(caps.map((c) => minutes.transform(c)))}.`;
  }
  return (
    $localize`This week runs on term values: ${count} of 7 days are in ${names}, fewer than the ${week.holidayWeekThresholdDays} that make a holiday week.` +
    ' ' +
    ceilings
  );
}

/** The reason and the numbers, in one paragraph. */
export function describeWeek(week: EffectiveSettings): string {
  return `${weekReason(week)} ${numbers(week.values, week.weeklyBudgetMinutes, week.bonusActive)}`;
}

function numbers(values: Record<string, number>, budget: number, bonus: boolean): string {
  const school = values['weekdayCapMinutes'];
  const weekend = bonus ? values['bonusWeekendCapMinutes'] : values['weekendCapMinutes'];
  const perDay =
    school === weekend
      ? $localize`${minutes.transform(school)} per day`
      : $localize`${minutes.transform(school)} on school days and ${minutes.transform(weekend)} at the weekend`;
  const budgetText = bonus
    ? $localize`${minutes.transform(budget)} with the bonus`
    : minutes.transform(budget);
  return $localize`Budget ${budgetText}, ${perDay}, screens off at ${values['cutoffHour']}\:00.`;
}

export interface PeriodDates {
  startDate: string;
  endDate: string;
}

/**
 * What saving a period would do to the current week, or null when it does not
 * touch it. When an existing period is edited, {@code original} is what it
 * covered before, so only the days that really change are named.
 */
export function holidayWarning(
  current: EffectiveSettings,
  holidayValues: Record<string, string | number>,
  period: PeriodDates,
  original?: PeriodDates,
): string | null {
  if (!complete(period)) {
    return null;
  }
  const inside = (p: PeriodDates | undefined, date: string) =>
    p !== undefined && complete(p) && p.startDate <= date && date <= p.endDate;

  const before = new Set(current.holidayDays);
  const after = new Set(
    current.days
      .map((d) => d.date)
      .filter((date) => (before.has(date) && !inside(original, date)) || inside(period, date)),
  );
  const becoming = current.days.filter((d) => after.has(d.date) && !before.has(d.date));
  const leaving = current.days.filter((d) => before.has(d.date) && !after.has(d.date));
  if (becoming.length === 0 && leaving.length === 0) {
    return null;
  }

  const parts: string[] = [];
  if (becoming.length > 0) {
    const names = list(becoming.map((d) => DAY_NAMES[d.dayOfWeek]));
    parts.push(
      becoming.length === 1
        ? $localize`${names} becomes a holiday day`
        : $localize`${names} become holiday days`,
    );
  }
  if (leaving.length > 0) {
    const names = list(leaving.map((d) => DAY_NAMES[d.dayOfWeek]));
    parts.push(
      leaving.length === 1
        ? $localize`${names} is no longer a holiday day`
        : $localize`${names} are no longer holiday days`,
    );
  }

  const count = after.size;
  const holidayWeek = count >= current.holidayWeekThresholdDays;
  let consequence: string;
  if (holidayWeek && current.scope === 'TERM') {
    consequence = $localize`That makes ${count} of 7, so the whole week switches to holiday values: budget ${minutes.transform(Number(holidayValues['weeklyMinutes']))}, screens off at ${holidayValues['cutoffHour']}\:00.`;
  } else if (!holidayWeek && current.scope === 'HOLIDAY') {
    consequence = $localize`That leaves ${count} of 7, so the week goes back to term values.`;
  } else if (holidayWeek) {
    consequence = $localize`The week still counts as holiday (${count} of 7).`;
  } else {
    consequence = $localize`The week stays on term values (${count} of 7); only the holiday days get the holiday ceiling.`;
  }

  return `${$localize`This changes the current week:`} ${parts.join('; ')}. ${consequence}`;
}

function complete(p: PeriodDates): boolean {
  return Boolean(p.startDate) && Boolean(p.endDate) && p.startDate <= p.endDate;
}

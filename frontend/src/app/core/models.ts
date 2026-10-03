/**
 * The shapes the API returns. These mirror the records in
 * `com.kiwih.screentime.web.dto` one for one.
 *
 * Fields that can be absent are optional here rather than nullable, because
 * the backend omits nulls from its JSON.
 */

export type Role = 'CHILD' | 'PARENT';
export type SessionType = 'FUN' | 'QUICK' | 'FILM';
export type SessionSource = 'TIMER' | 'MANUAL';
export type DayOfWeek =
  'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';

/**
 * Every total the dashboard shows, with the budget and what has been used kept
 * separate from what is left, so the arithmetic can be shown and not just the
 * answer. All in seconds: time of use is counted to the second.
 */
export interface Balance {
  weeklyBudgetSeconds: number;
  adjustmentSeconds: number;
  weekUsedSeconds: number;
  remainingWeekSeconds: number;
  dailyCapSeconds: number;
  dayUsedSeconds: number;
  remainingTodaySeconds: number;
  quickBudgetSeconds: number;
  quickUsedSeconds: number;
  remainingQuickSeconds: number;
  availableNowSeconds: number;
}

export interface Device {
  id: number;
  name: string;
  active: boolean;
}

export interface Session {
  id: number;
  day: string;
  type: SessionType;
  source: SessionSource;
  deviceId: number;
  deviceName?: string;
  startedAt: string;
  endedAt?: string;
  /** to the second; a running session reports what it has used so far */
  seconds: number;
  running: boolean;
  autoClosed: boolean;
  note?: string;
  createdBy?: string;
  /** the checklist items ticked for it, as they read then; empty when none were due */
  checklist: ChecklistTick[];
}

/** One item ticked for an entry. */
export interface ChecklistTick {
  itemId: number;
  text: string;
  tickedAt: string;
}

/** One item the child ticks today before screen time. */
export interface ChecklistDue {
  id: number;
  text: string;
}

/** A reminder as the parents manage it. No weekdays means every day. */
export interface ChecklistItem {
  id: number;
  text: string;
  weekdays: DayOfWeek[];
  validFrom?: string;
  validUntil?: string;
  sortOrder: number;
  dueToday: boolean;
  createdBy?: string;
  createdAt: string;
}

export interface ChecklistItemInput {
  text: string;
  weekdays: DayOfWeek[];
  validFrom?: string;
  validUntil?: string;
}

export interface OpenSession {
  id: number;
  type: SessionType;
  deviceId: number;
  deviceName?: string;
  startedAt: string;
  elapsedSeconds: number;
  /** what was available without this session; the countdown subtracts the time since startedAt */
  countdownAgainstSeconds: number;
}

export interface Day {
  date: string;
  dayOfWeek: DayOfWeek;
  capSeconds: number;
  usedSeconds: number;
  remainingSeconds: number;
  quickUsedSeconds: number;
  today: boolean;
  future: boolean;
}

export interface DayDetail extends Day {
  entries: Session[];
}

export interface Adjustment {
  id: number;
  weekStart: string;
  minutes: number;
  reason: string;
  createdBy?: string;
  createdAt: string;
  fromWeeklyCheck: boolean;
}

export interface ReportedDevice {
  deviceId: number;
  deviceName?: string;
  minutes: number;
}

export interface WeeklyCheck {
  id: number;
  weekStart: string;
  loggedMinutes: number;
  reportedMinutes: number;
  differenceMinutes: number;
  penaltyMinutes: number;
  clean: boolean;
  deliberate: boolean;
  /** what the week was checked against, kept with the check */
  budgetMinutes: number;
  toleranceMinutes: number;
  settingsScope: SettingsScope;
  checkedAt: string;
  reported: ReportedDevice[];
}

export interface Account {
  displayName: string;
  today: string;
  weekStart: string;
  balance: Balance;
  holidayWeek: boolean;
  bonusActive: boolean;
  cutoffHour: number;
  /** the values in force this week and why */
  rules: EffectiveSettings;
  /** the cut off has passed: screens are off for the evening */
  screensOff: boolean;
  openSession?: OpenSession;
  /** what the child ticks before screen time today; empty when nothing is due */
  checklist: ChecklistDue[];
  week: Day[];
  todayEntries: Session[];
  weekAdjustments: Adjustment[];
  devices: Device[];
}

export interface Week {
  weekStart: string;
  balance: Balance;
  holidayWeek: boolean;
  bonusActive: boolean;
  days: DayDetail[];
  adjustments: Adjustment[];
  check?: WeeklyCheck;
}

export interface WeekSummary {
  weekStart: string;
  budgetSeconds: number;
  adjustmentSeconds: number;
  usedSeconds: number;
  remainingSeconds: number;
  holidayWeek: boolean;
  bonusActive: boolean;
  check?: WeeklyCheck;
}

export interface WeekFlags {
  weekStart: string;
  holiday: boolean;
  bonusActive: boolean;
}

export interface CheckResult {
  check: WeeklyCheck;
  moreLoggedThanReported: boolean;
  bonusSetForFollowingWeek: boolean;
  followingWeek: string;
  penalty?: Adjustment;
}

export interface User {
  id: number;
  username: string;
  displayName: string;
  role: Role;
  active: boolean;
  createdAt: string;
}

export interface LoginResult {
  token: string;
  expiresAt: string;
  userId: number;
  username: string;
  displayName: string;
  role: Role;
}

export interface ApiError {
  status: number;
  error: string;
  message: string;
  at: string;
  details?: Record<string, unknown>;
}

export type Settings = Record<string, string>;

export type SettingsScope = 'TERM' | 'HOLIDAY' | 'GLOBAL';

export interface SettingRow {
  id: number;
  key: string;
  value: string;
  validFrom: string;
  /** absent for the seeded defaults */
  createdBy?: string;
  createdAt: string;
}

/** One value set: in force this week, next week, and every row behind it, oldest first. */
export interface ScopeSettings {
  scope: SettingsScope;
  thisWeek: Settings;
  nextWeek: Settings;
  history: SettingRow[];
}

/** One line of the change log. */
export interface SettingChange {
  at: string;
  by?: string;
  kind: 'SETTING' | 'HOLIDAY_PERIOD';
  action: string;
  scope?: SettingsScope;
  key?: string;
  from?: string;
  to?: string;
  validFrom?: string;
}

export interface SettingsOverview {
  thisWeek: string;
  nextWeek: string;
  term: ScopeSettings;
  holiday: ScopeSettings;
  general: ScopeSettings;
  changes: SettingChange[];
}

/** A save: changed values of one scope, from this Monday (default) or next Monday. */
export interface SettingsChange {
  scope: SettingsScope;
  values: Settings;
  validFrom?: string;
}

export interface EffectiveDay {
  date: string;
  dayOfWeek: DayOfWeek;
  holiday: boolean;
  /** the ceiling of the day, bonus included */
  capMinutes: number;
}

/** The values that apply to a week, and why. */
export interface EffectiveSettings {
  weekStart: string;
  scope: 'TERM' | 'HOLIDAY';
  holidayDayCount: number;
  holidayWeekThresholdDays: number;
  holidayDays: string[];
  holidayPeriodName?: string;
  holidayPeriodNames: string[];
  bonusActive: boolean;
  /** the bonus included when it is active */
  weeklyBudgetMinutes: number;
  /** the set that won: minutes, and the cut off in hours */
  values: Record<string, number>;
  days: EffectiveDay[];
}

export interface HolidayPeriod {
  id: number;
  name: string;
  startDate: string;
  endDate: string;
  /** both ends counted */
  days: number;
  createdBy?: string;
  createdAt: string;
}

export interface HolidayPeriodInput {
  name: string;
  startDate: string;
  endDate: string;
}

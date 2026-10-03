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
 * answer.
 */
export interface Balance {
  weeklyBudgetMinutes: number;
  adjustmentMinutes: number;
  weekUsedMinutes: number;
  remainingWeekMinutes: number;
  dailyCapMinutes: number;
  dayUsedMinutes: number;
  remainingTodayMinutes: number;
  quickBudgetMinutes: number;
  quickUsedMinutes: number;
  remainingQuickMinutes: number;
  availableNowMinutes: number;
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
  minutes: number;
  running: boolean;
  autoClosed: boolean;
  note?: string;
  createdBy?: string;
}

export interface OpenSession {
  id: number;
  type: SessionType;
  deviceId: number;
  deviceName?: string;
  startedAt: string;
  elapsedMinutes: number;
  countdownAgainstMinutes: number;
}

export interface Day {
  date: string;
  dayOfWeek: DayOfWeek;
  capMinutes: number;
  usedMinutes: number;
  remainingMinutes: number;
  quickUsedMinutes: number;
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
  /** the cut off has passed: screens are off for the evening */
  screensOff: boolean;
  openSession?: OpenSession;
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
  budgetMinutes: number;
  adjustmentMinutes: number;
  usedMinutes: number;
  remainingMinutes: number;
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

/** The settings keys, in the order the parent screen shows them. */
export const SETTING_KEYS = [
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

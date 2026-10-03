# Specification

A screen time account for a family. A child books their own screen time, parents
keep the account and compare the log with what the devices report once a week.

The app counts, it does not enforce. Blocking stays with the built in limits of
iOS, Android, Windows and the consoles. Everything here exists to make the
remaining budget visible and the weekly review factual.

## Roles

| Role | Who | What they can do |
| --- | --- | --- |
| CHILD | one person | Book their own time, see their account and history |
| PARENT | one or more | Everything, including corrections and settings |

Authentication is username and password, hashed with BCrypt, with a JWT access
token valid for 30 days so the child is not logged out constantly. No OAuth, no
Keycloak, no open registration. The first parent account is created at startup
from `APP_ADMIN_USER` and `APP_ADMIN_PASSWORD`; the application refuses to start
if they are missing. Further accounts are created in the app by a parent.

## Settings

All values live in a `settings` table and are editable by a parent. These are
the defaults.

| Key | Default | Meaning |
| --- | --- | --- |
| weeklyMinutes | 480 | Budget per week, Monday to Sunday |
| weekdayCapMinutes | 60 | Ceiling for Monday to Friday |
| weekendCapMinutes | 120 | Ceiling for Saturday and Sunday |
| quickDailyMinutes | 15 | Daily budget for looking things up |
| cutoffHour | 20 | No session starts at or after this hour |
| bonusMinutes | 60 | Added to the week after a clean week |
| bonusWeekendCapMinutes | 150 | Weekend ceiling while a bonus is active |
| maxPenaltyMinutes | 120 | Largest deduction a single check can cause |
| toleranceMinutes | 10 | Difference still counted as a match |
| manualMaxMinutes | 240 | Largest single manual entry a child may book |

The daily ceilings must add up to more than the weekly budget, otherwise the
weekly budget never binds and only the daily ceilings do any work. With the
defaults: 5 × 60 + 2 × 120 = 540 against a weekly 480. Validate this on save and
reject a settings change that breaks it, with a message naming both numbers.

## Session types

| Type | Counts against week | Counts against day | Who may start |
| --- | --- | --- | --- |
| FUN | yes | yes | CHILD, PARENT |
| QUICK | no | against the quick budget only | CHILD, PARENT |
| FILM | no | no | PARENT only |

QUICK is for looking something up: a question with an answer, that ends when the
answer is there. FILM is the family film night on Friday, Saturday and Sunday.

## Core rules

1. Unused time expires. Nothing carries to the next day or the next week.
2. Available now is the minimum of the remaining week and the remaining day.
3. The daily ceiling follows the weekday, unless the week is flagged as a
   holiday week, in which case the weekend ceiling applies every day.
4. No session of any type starts at or after the cut off hour, except FILM.
5. A clean week sets the bonus for the following week: the weekly budget rises
   by `bonusMinutes` and the weekend ceiling rises to `bonusWeekendCapMinutes`.
   The bonus is not cumulative; it is set or not set, per week.

## Time, dates and manipulation

This is the part that decides whether the app is trustworthy. Get it right
before anything else.

- Every timestamp is set by the server. A date or time in a request body is
  ignored, never used. The only exception is a parent booking a past day, where
  the date is an explicit, authorised field.
- Store instants as `timestamptz` in UTC. Derive the calendar day and the week
  in `Europe/Zurich` with `java.time`, never by adding or subtracting hours.
- The week runs Monday 00:00 to Sunday 23:59:59 local time. A week is identified
  by its Monday as a `LocalDate`.
- Daylight saving time means two days a year are 23 or 25 hours long. Budgets
  are in minutes of use and do not change on those days, but tests must cover
  the last Sunday in March and October so nobody later "fixes" a day length.
- A session belongs to the local day on which it started. A session that starts
  at 19:50 and ends at 20:30 counts fully to that day.
- One open session per user at a time. Starting a second returns 409 with the id
  of the open one.
- A scheduler closes any session still open at 23:59 local time, sets
  `autoClosed = true`, and caps its duration at the remaining daily ceiling so
  one forgotten stop cannot wipe out a week. Parents see the flag and decide.
- A child may book manually only for the current local day, only before the cut
  off hour, and at most `manualMaxMinutes` in one entry. Any other date returns
  403. This is deliberate: without it, the weekly comparison is pointless.
- Parents may book, edit and delete any day.
- Every create, update and delete on a session or adjustment writes an
  `audit_log` row with entity, id, action, old value, new value, user and time.

Compute balances on read from the stored sessions and adjustments. Do not keep a
running total in a column. The data set is tiny and a derived total that drifts
out of sync is a bug that is very hard to find later.

## Data model

- `users`: id, username, password_hash, display_name, role, active, created_at
- `devices`: id, name, active, sort_order
- `settings`: key, value, updated_at, updated_by
- `sessions`: id, user_id, started_at, ended_at, minutes, device_id, type,
  source (TIMER, MANUAL), created_by, auto_closed, note
- `adjustments`: id, week_start, minutes (signed), reason, created_by, created_at
- `weekly_checks`: id, week_start, logged_minutes, reported_minutes, difference,
  penalty_minutes, clean, deliberate, checked_by, checked_at
- `week_flags`: week_start, holiday, bonus_active
- `audit_log`: id, entity, entity_id, action, old_value, new_value, user_id, at

Seed `devices` with iPad, iMac, Phone, PlayStation, TV. Seed `settings` with the
defaults above. Seed no users.

## API

REST under `/api`, documented with springdoc OpenAPI.

| Method | Path | Role | Notes |
| --- | --- | --- | --- |
| POST | /api/auth/login | all | returns the JWT |
| GET | /api/account/current | all | the dashboard payload, see below |
| GET | /api/account/week | all | `?start=YYYY-MM-DD`, all seven days |
| GET | /api/account/history | all | `?weeks=12`, one row per week |
| POST | /api/sessions/start | all | `{ deviceId, type }` |
| POST | /api/sessions/stop | all | closes the caller's open session |
| POST | /api/sessions/manual | all | `{ minutes, deviceId, type, date? }` |
| GET | /api/sessions | all | `?from=&to=`, a child sees only their own |
| PUT | /api/sessions/{id} | PARENT | |
| DELETE | /api/sessions/{id} | PARENT | |
| POST | /api/checks | PARENT | `{ weekStart, reported: [{deviceId, minutes}], deliberate }` |
| POST | /api/adjustments | PARENT | `{ weekStart, minutes, reason }` |
| PUT | /api/settings | PARENT | |
| GET/POST/PUT | /api/users | PARENT | |

`GET /api/account/current` returns everything the dashboard needs in one call:
remaining week, remaining today, remaining quick budget, available now, the
daily ceiling in force, bonus active, cut off hour, whether screens are off
right now, the open session if there is one, and the seven day strip.

### The weekly check

Input is the minutes each device reports for that week. Logged is the sum of
FUN minutes booked in that week. Difference is reported minus logged.

- Difference within `toleranceMinutes`: the week is clean, no deduction, the
  bonus is set for the following week.
- Difference above tolerance: an adjustment of minus the difference is written
  against the following week, capped at `maxPenaltyMinutes`. No bonus.
- `deliberate` adds 60 minutes to the penalty before the cap.
- Difference below minus tolerance (more booked than the devices saw): no
  deduction, no bonus, flag it in the response so parents can look at the
  entries.

A check for a week that already has one replaces it, and reverses the
adjustment the previous check created. Do not stack penalties.

## Frontend

Angular, standalone components, signals, Angular Material. Mobile and tablet
first; this is used on an iPad and a phone, rarely on a desktop. Interface
language English, prepared for i18n.

Child view:
- Remaining week as the largest element on the screen
- Remaining today and remaining quick budget below it
- A seven day strip, today marked
- Start and stop, with device and type
- A running session showing a countdown against what is available
- Today's entries, without a delete control
- History of past weeks, read only
- The rules in plain language as their own page

Parent view adds:
- The weekly check: reported minutes per device, difference computed live, save
- Adjustments with a reason, settings, holiday flag, user management
- Edit and delete sessions

The child must always be able to see why a number is what it is. A balance that
drops without an explanation is the fastest way to lose the child's cooperation.

## Out of scope

No device monitoring, no reading of Apple Screen Time or Google Family Link, no
blocking, no push notifications, no multi tenancy, no statistics beyond the
weekly history. Keep version one small.

## Build order

1. Data model and Flyway migrations
2. Business rules as a plain service class with no Spring annotations, with
   tests covering week boundaries, daylight saving, the cut off, the daily
   ceiling, the bonus and the penalty cap
3. API with Spring Security and the role rules
4. Frontend
5. Docker, compose, and `docs/DEPLOY.md`

Stop after each step and show what was built before continuing.

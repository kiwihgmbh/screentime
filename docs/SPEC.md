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

All values live in the `settings` table and are editable by a parent. Every
setting exists twice: one value for term time, one for the holidays.

| Key | Term default | Holiday default | Meaning |
| --- | --- | --- | --- |
| weeklyMinutes | 480 | 720 | Budget for the week, Monday to Sunday |
| weekdayCapMinutes | 60 | 120 | Ceiling Monday to Friday |
| weekendCapMinutes | 120 | 120 | Ceiling Saturday and Sunday |
| quickDailyMinutes | 15 | 15 | Daily budget for looking things up |
| cutoffHour | 20 | 21 | No session starts at or after this hour |
| bonusMinutes | 60 | 60 | Added after a clean week |
| bonusWeekendCapMinutes | 150 | 150 | Weekend ceiling while a bonus runs |
| maxPenaltyMinutes | 120 | 120 | Largest deduction per check |
| toleranceMinutes | 10 | 10 | Difference still counted as a match |
| manualMaxMinutes | 240 | 240 | Largest single manual entry by the child |
| deliberatePenaltyMinutes | 60 | 60 | Added to the penalty of a week marked deliberate |

One more setting belongs to neither set, because it decides between them. It
has the scope `GLOBAL`.

| Key | Default | Meaning |
| --- | --- | --- |
| holidayWeekThresholdDays | 4 | Holiday days that make a whole week a holiday week |

### Holiday periods

A parent enters the school holidays as periods: a name, a start date and an
end date, both inclusive. A day is a holiday day if it falls inside a period.
Periods never overlap; an overlapping one is refused with 409 naming the
period in the way. Nothing is seeded.

### Which set applies to a week

Count the holiday days in the week. If `holidayWeekThresholdDays` or more of
the seven are holiday days, the holiday set applies to the whole week.
Otherwise the term set applies, but the daily ceiling on each individual
holiday day uses the holiday value. Everything else in a term week, the weekly
budget, the cut off and the quick budget, stays a term value.

A weekly budget cannot be split across two value sets without becoming
impossible to explain to a child. Daily ceilings can.

### Settings are versioned

A settings row is never changed in place. Changing a value writes a new row
with the Monday from which it applies (`valid_from`). The values that apply to
a week, the threshold included, are those in force on that week's Monday; two
rows for the same Monday are decided by the later one. A change made today
cannot alter last week's budget or a weekly check that already happened.
Weekly checks also store the budget and tolerance they were computed against,
so the history stays readable even if somebody later edits the past.

### Validation

A settings change is refused, with a message naming the actual numbers, when
any of these fail. Each set is checked on its own, and every broken rule is
reported at once.

1. The daily ceilings exceed the weekly budget:
   5 × weekdayCap + 2 × weekendCap > weeklyMinutes. Otherwise the weekly
   budget never binds and only the daily ceilings do any work.
2. The same holds in a bonus week:
   weeklyMinutes + bonusMinutes < 5 × weekdayCap + 2 × bonusWeekendCap.
3. bonusWeekendCapMinutes >= weekendCapMinutes.
4. cutoffHour is between 12 and 23.
5. Every minute value is >= 0, and weeklyMinutes <= 10080.

holidayWeekThresholdDays is between 1 and 7.

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
3. The daily ceiling follows the weekday, and comes from the holiday set on a
   holiday day or in a holiday week (see "Which set applies to a week").
4. No session of any type starts at or after the cut off hour, except FILM.
5. A clean week sets the bonus for the following week: the weekly budget rises
   by `bonusMinutes` and the weekend ceiling rises to `bonusWeekendCapMinutes`.
   The bonus is not cumulative; it is set or not set, per week.

## Checklist

Parents set reminders the child ticks before screen time: "homework first",
"laundry". An item is due always, or only on some weekdays, or only from one
date to another (both ends inclusive, either end may be open), or both; a
single day is a range of one day. Nothing is seeded.

Every start of screen time by the child, by timer or by hand, needs every
item due that day ticked, every time. The list does not disappear once
ticked: the next start asks again, because a list that is clicked away once
is forgotten. The ticks are sent with the start request and checked against
the server's own day and list; a start with an item left unticked returns 409
with the items still to tick. Each tick is stored with the session it unlocked
and a copy of the item's text, at the server's time.

Looking things up is not blocked, because it is often the homework itself. A
parent is not blocked. Removing an item switches it off; it is never deleted,
so past ticks stay readable.

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
- Time of use is counted in seconds. A timer session stores the whole seconds
  that passed, so a 40 second session costs 40 seconds and part minutes add up
  instead of being lost. Budgets, ceilings, adjustments and manual entries are
  whole minutes, because that is what people enter. Balances are computed and
  reported in seconds.
- The weekly check compares the logged seconds, rounded to the nearest minute
  (half up), with the whole minutes the devices report.
- A session belongs to the local day on which it started. A session that starts
  at 19:50 and ends at 20:30 counts fully to that day.
- One open session per user at a time. Starting a second returns 409 with the id
  of the open one.
- A scheduler closes any session still open at 23:59 local time, sets
  `autoClosed = true`, and caps its duration at the remaining daily ceiling,
  to the second, so one forgotten stop cannot wipe out a week. Parents see the
  flag and decide.
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
- `settings`: id, scope (TERM, HOLIDAY, GLOBAL), key, value, valid_from (a
  Monday), created_by, created_at. Append only: the database refuses an update.
- `holiday_periods`: id, name, start_date, end_date, created_by, created_at.
  Both dates inclusive; the database refuses two periods sharing a day.
- `sessions`: id, user_id, started_at, ended_at, duration_seconds, device_id,
  type, source (TIMER, MANUAL), created_by, auto_closed, note
- `adjustments`: id, week_start, minutes (signed), reason, created_by, created_at
- `weekly_checks`: id, week_start, logged_minutes, reported_minutes, difference,
  penalty_minutes, clean, deliberate, budget_minutes, tolerance_minutes,
  settings_scope, checked_by, checked_at
- `week_flags`: week_start, bonus_active
- `checklist_items`: id, text, weekdays (null for every day), valid_from,
  valid_until, sort_order, active, created_by, created_at
- `checklist_ticks`: id, session_id, item_id, item_text, user_id, ticked_at;
  one row per item ticked for one start
- `audit_log`: id, entity, entity_id, action, old_value, new_value, user_id, at

Seed `devices` with iPad, iMac, Phone, PlayStation, TV. Seed `settings` with the
defaults above, in force since long before any week the app shows. Seed no
users and no holiday periods.

## API

REST under `/api`, documented with springdoc OpenAPI.

| Method | Path | Role | Notes |
| --- | --- | --- | --- |
| POST | /api/auth/login | all | returns the JWT |
| GET | /api/account/current | all | the dashboard payload, see below |
| GET | /api/account/week | all | `?start=YYYY-MM-DD`, all seven days |
| GET | /api/account/history | all | `?weeks=12`, one row per week |
| POST | /api/sessions/start | all | `{ deviceId, type, checklistItemIds? }` |
| POST | /api/sessions/stop | all | closes the caller's open session |
| POST | /api/sessions/manual | all | `{ minutes, deviceId, type, date?, checklistItemIds? }` |
| GET | /api/sessions | all | `?from=&to=`, a child sees only their own |
| PUT | /api/sessions/{id} | PARENT | |
| DELETE | /api/sessions/{id} | PARENT | |
| POST | /api/checks | PARENT | `{ weekStart, reported: [{deviceId, minutes}], deliberate }` |
| POST | /api/adjustments | PARENT | `{ weekStart, minutes, reason }` |
| GET | /api/settings | PARENT | both value sets and the threshold: in force this week and next, every row, and the change log |
| PUT | /api/settings | PARENT | `{ scope, values, validFrom? }`, see below |
| GET | /api/settings/effective | all | `?week=YYYY-MM-DD`, the values in force for that week and why |
| GET | /api/checklist | PARENT | every item in order, with whether it is due today |
| POST | /api/checklist | PARENT | `{ text, weekdays[], validFrom?, validUntil? }`, added at the end |
| PUT | /api/checklist/{id} | PARENT | the same body; past ticks keep the old text |
| PUT | /api/checklist/order | PARENT | `{ ids }`, every item exactly once |
| DELETE | /api/checklist/{id} | PARENT | switches the item off |
| GET | /api/holidays | PARENT | `?from=&to=`, the periods sharing a day with the range, or all |
| POST | /api/holidays | PARENT | `{ name, startDate, endDate }`; 409 naming the period it overlaps |
| PUT | /api/holidays/{id} | PARENT | the same body; a period never conflicts with itself |
| DELETE | /api/holidays/{id} | PARENT | |
| GET/POST/PUT | /api/users | PARENT | |

### Changing settings

`PUT /api/settings` changes values of one scope (TERM, HOLIDAY or GLOBAL).
`validFrom` is the Monday of the current week, which is the default, or next
Monday; both are worked out from the server's clock and any other date returns
400. The change is merged with the values in force on that Monday and
validated as a whole, and again for every later Monday on which a change to
the same scope is already planned, so a change this week cannot break one
already made for next week. Only keys whose value differs get a new row. Every
new row writes an audit entry with the old and the new value.

The change log in `GET /api/settings` says who changed what, when, and from
which value to which. Settings come from their own rows, the value before a
row being the previous row for the same key; holiday periods come from the
audit log.

### The values in force

`GET /api/settings/effective` returns, for one week: which set won (`scope`),
how many holiday days the week has against `holidayWeekThresholdDays`, which
days they are, the name of the holiday period if there is one, whether the
bonus is active, the weekly budget with the bonus, every value of the winning
set, and each day's ceiling. `GET /api/account/current` carries the same for
the current week as `rules`, so the child's view can say "holiday rules"
instead of showing an unexplained larger number.

A weekly check also returns the budget, the tolerance and the scope it was
computed against.

`GET /api/account/current` returns everything the dashboard needs in one call:
remaining week, remaining today, remaining quick budget, available now, the
daily ceiling in force, bonus active, cut off hour, whether screens are off
right now, the open session if there is one, and the seven day strip. Every
duration in it is in seconds. The open session carries what was available
before it started, so the countdown is that minus the time since its start,
however often the page is reloaded. It also carries `checklist`, the items
the child ticks before screen time today. Every entry carries the checklist
items ticked for it, as they read then.

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
- The checklist due today at the top, every time the page opens, one tick box
  per item; screen time cannot start until every box is ticked, and the boxes
  are empty again after each start
- Remaining week as the largest element on the screen. The big numbers show
  seconds only once less than an hour is left; entries and the running
  countdown are always exact to the second
- Remaining today and remaining quick budget below it
- A seven day strip, today marked
- Start and stop, with device and type
- A running session showing a countdown against what is available
- Today's entries, without a delete control
- History of past weeks, read only
- The rules in plain language as their own page, with the values in force this
  week and a line naming why they are what they are: term or holiday rules,
  and which days are holidays. No form controls on that page.

Parent view adds:
- The weekly check: reported minutes per device, difference computed live, save
- Adjustments with a reason, settings, holiday periods, user management
- The checklist page: the items in the child's order with when each is due,
  add, edit, move up and down, remove; weekday toggles and optional first and
  last day
- On every entry, the checklist items ticked for it
- The settings page: one sentence on what the current week runs on and why;
  the term values and the holiday values, each field with its value in force,
  its rule in one line and an inline error when the rule breaks, Save disabled
  while any rule is broken, and a choice between this week and next week that
  says which weeks it touches; the holiday periods as a list with add and
  edit, warning when a period touches the current week and naming the days
  that change; the threshold; and the change log at the bottom
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

-- Sessions count to the second.
--
-- Until now a timer was rounded down to whole minutes when it stopped. A 40
-- second session cost nothing, and every session lost its last partial minute,
-- so the log ran below what the devices report and a week could fail its
-- check on rounding alone. The duration is now kept in seconds and every
-- balance is computed from that.
--
-- Existing rows become minutes × 60. Their real seconds are gone, and
-- recomputing them from started_at and ended_at would undo the cap on
-- automatically closed sessions and change numbers the child has already seen.

alter table sessions add column duration_seconds integer;

update sessions set duration_seconds = minutes * 60 where minutes is not null;

alter table sessions drop constraint ck_sessions_minutes_positive;
alter table sessions drop constraint ck_sessions_closed;
alter table sessions drop column minutes;

alter table sessions add constraint ck_sessions_seconds_positive
    check (duration_seconds is null or duration_seconds >= 0);

-- a closed session always carries its duration, an open one never does
alter table sessions add constraint ck_sessions_closed check (
    (ended_at is null and duration_seconds is null) or
    (ended_at is not null and duration_seconds is not null)
);

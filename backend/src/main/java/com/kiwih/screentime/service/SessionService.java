package com.kiwih.screentime.service;

import com.kiwih.screentime.domain.*;
import com.kiwih.screentime.repo.DeviceRepository;
import com.kiwih.screentime.repo.SessionRepository;
import com.kiwih.screentime.rules.*;
import com.kiwih.screentime.security.AppPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Starting, stopping, booking, correcting and deleting screen time.
 *
 * Two things hold throughout. Every timestamp written here comes from the
 * server clock, never from a request. And a session belongs to the account, the
 * child's, while {@code created_by} records who entered it, so a correction a
 * parent makes at ten in the evening is visible as exactly that.
 */
@Service
public class SessionService {

    /**
     * Where a manual entry for another day is placed on the clock. A correction
     * has no real start time; only the day it belongs to carries meaning, and
     * midday is unambiguous on both days the clocks change.
     */
    private static final LocalTime MANUAL_ENTRY_TIME = LocalTime.NOON;

    private final SessionRepository sessions;
    private final DeviceRepository devices;
    private final SettingsService settingsService;
    private final AccountResolver accounts;
    private final WeekService weeks;
    private final AuditService audit;
    private final Clock clock;

    public SessionService(SessionRepository sessions, DeviceRepository devices,
                          SettingsService settingsService, AccountResolver accounts,
                          WeekService weeks, AuditService audit, Clock clock) {
        this.sessions = sessions;
        this.devices = devices;
        this.settingsService = settingsService;
        this.accounts = accounts;
        this.weeks = weeks;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<Session> openSession(Long userId) {
        return sessions.findByUserIdAndEndedAtIsNull(userId);
    }

    @Transactional
    public Session start(AppPrincipal caller, Long deviceId, SessionType type) {
        User account = accounts.resolve(caller);
        ScreentimeRules rules = settingsService.rules();
        Instant now = clock.instant();

        Long openId = openSession(account.getId()).map(Session::getId).orElse(null);
        rules.requireStartAllowed(caller.role(), type, now, openId);
        requireDevice(deviceId, caller);

        Session saved = sessions.save(
                Session.startTimer(account.getId(), deviceId, type, caller.userId(), now));
        audit.created(AuditService.SESSION, saved.getId(), SessionSnapshot.of(saved), caller.userId());
        return saved;
    }

    /**
     * Closes the session running on the account. The duration is the real
     * elapsed time: a stop the child chose is not capped, because the child
     * stopping honestly is the behaviour the whole app is trying to encourage.
     */
    @Transactional
    public Session stop(AppPrincipal caller) {
        User account = accounts.resolve(caller);
        Session open = openSession(account.getId()).orElseThrow(() -> new RuleViolation(
                RuleViolation.Kind.INVALID, "Nothing is running at the moment."));

        SessionSnapshot before = SessionSnapshot.of(open);
        Instant now = clock.instant();
        WeekCalendar calendar = settingsService.calendar();
        open.close(now, calendar.secondsBetween(open.getStartedAt(), now), false);

        audit.updated(AuditService.SESSION, open.getId(), before,
                SessionSnapshot.of(open), caller.userId());
        return open;
    }

    /**
     * Books time by hand. A child may only book the current day and only before
     * the cut off; a parent may book any day, which is how the account gets
     * corrected after the fact.
     *
     * A date in the request is only ever honoured for a parent. For a child it
     * is checked against the server's today and refused if it differs, rather
     * than quietly accepted.
     */
    @Transactional
    public Session bookManually(AppPrincipal caller, Long deviceId, SessionType type,
                                int minutes, LocalDate requestedDate, String note) {
        User account = accounts.resolve(caller);
        ScreentimeRules rules = settingsService.rules();
        Instant now = clock.instant();
        LocalDate today = rules.calendar().dayOf(now);
        LocalDate day = requestedDate != null ? requestedDate : today;

        rules.requireManualBookingAllowed(caller.role(), type, day, now, minutes);
        requireDevice(deviceId, caller);

        Instant startedAt = day.equals(today)
                ? now
                : day.atTime(MANUAL_ENTRY_TIME).atZone(rules.calendar().zone()).toInstant();

        Session saved = sessions.save(Session.manual(
                account.getId(), deviceId, type, caller.userId(), startedAt, minutes, note));
        audit.created(AuditService.SESSION, saved.getId(), SessionSnapshot.of(saved), caller.userId());
        return saved;
    }

    /**
     * A parent's correction. Only closed sessions can be corrected: a running
     * one is stopped first, so there is never a question about what its
     * duration was at the time of the edit.
     */
    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public Session update(AppPrincipal caller, Long id, Integer minutes, Long deviceId,
                         SessionType type, LocalDate date, String note) {
        Session session = sessions.findById(id).orElseThrow(() -> new NotFoundException("Session", id));
        if (session.isOpen()) {
            throw new RuleViolation(RuleViolation.Kind.INVALID,
                    "This session is still running. Stop it before correcting it.");
        }
        SessionSnapshot before = SessionSnapshot.of(session);
        WeekCalendar calendar = settingsService.calendar();

        if (deviceId != null) {
            requireDevice(deviceId, caller);
            session.setDeviceId(deviceId);
        }
        if (type != null) {
            session.setType(type);
        }
        if (note != null) {
            session.setNote(note.isBlank() ? null : note);
        }
        if (date != null) {
            // move the session to another day, keeping its time of day
            LocalTime timeOfDay = calendar.timeOf(session.getStartedAt());
            session.setStartedAt(date.atTime(timeOfDay).atZone(calendar.zone()).toInstant());
        }
        if (minutes != null) {
            if (minutes < 0) {
                throw new RuleViolation(RuleViolation.Kind.INVALID,
                        "A session cannot have negative minutes.");
            }
            // a correction is entered in whole minutes, like a manual entry
            session.setDurationSeconds(Durations.seconds(minutes));
        }
        // keep the end consistent with the start and the duration
        session.setEndedAt(session.getStartedAt().plusSeconds(session.countedSeconds()));

        audit.updated(AuditService.SESSION, session.getId(), before,
                SessionSnapshot.of(session), caller.userId());
        return session;
    }

    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public void delete(AppPrincipal caller, Long id) {
        Session session = sessions.findById(id).orElseThrow(() -> new NotFoundException("Session", id));
        SessionSnapshot before = SessionSnapshot.of(session);
        sessions.delete(session);
        audit.deleted(AuditService.SESSION, id, before, caller.userId());
    }

    /** A child sees only their own entries; the role rule is applied here, not in the view. */
    @Transactional(readOnly = true)
    public List<Session> list(AppPrincipal caller, LocalDate from, LocalDate to) {
        User account = accounts.resolve(caller);
        WeekCalendar calendar = settingsService.calendar();
        LocalDate start = from != null ? from : calendar.weekStartOf(calendar.dayOf(clock.instant()));
        LocalDate end = to != null ? to : start.plusDays(6);
        if (end.isBefore(start)) {
            throw new RuleViolation(RuleViolation.Kind.INVALID, "The range ends before it starts.");
        }
        return sessions.findStartedBetween(account.getId(),
                calendar.startOfDay(start), calendar.endOfDayExclusive(end));
    }

    @Transactional(readOnly = true)
    public List<Device> activeDevices() {
        return devices.findAllByActiveTrueOrderBySortOrderAscNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Device> allDevices() {
        return devices.findAllByOrderBySortOrderAscNameAsc();
    }

    private void requireDevice(Long deviceId, AppPrincipal caller) {
        Device device = devices.findById(deviceId)
                .orElseThrow(() -> new NotFoundException("Device", deviceId));
        // a retired device can still appear in a parent's correction of an old
        // week, but nobody starts new time on one
        if (!device.isActive() && !caller.isParent()) {
            throw new RuleViolation(RuleViolation.Kind.NOT_ALLOWED,
                    device.getName() + " is not in use any more.");
        }
    }

    /** Called by the scheduler. Package visible so only the scheduler reaches it. */
    @Transactional
    Session closeAutomatically(Session open, Instant closeAt, int seconds, Long byUserId) {
        SessionSnapshot before = SessionSnapshot.of(open);
        open.close(closeAt, seconds, true);
        Session saved = sessions.save(open);
        audit.updated(AuditService.SESSION, saved.getId(), before,
                SessionSnapshot.of(saved), byUserId);
        return saved;
    }

    List<Session> allOpenSessions() {
        return sessions.findByEndedAtIsNull();
    }

    WeekService weeks() {
        return weeks;
    }
}

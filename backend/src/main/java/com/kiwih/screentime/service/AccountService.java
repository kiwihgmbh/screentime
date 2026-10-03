package com.kiwih.screentime.service;

import com.kiwih.screentime.domain.*;
import com.kiwih.screentime.repo.DeviceRepository;
import com.kiwih.screentime.repo.UserRepository;
import com.kiwih.screentime.repo.WeeklyCheckRepository;
import com.kiwih.screentime.rules.*;
import com.kiwih.screentime.security.AppPrincipal;
import com.kiwih.screentime.web.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds what the screens show.
 *
 * The dashboard comes back in one call, with the budget, the adjustment and
 * what has been used all reported separately from what is left. The child has
 * to be able to see why a number is what it is; a single "42 minutes left" with
 * no arithmetic behind it is how you lose their cooperation.
 */
@Service
public class AccountService {

    private final AccountResolver accounts;
    private final SettingsService settingsService;
    private final BalanceService balances;
    private final WeekService weeks;
    private final WeeklyCheckRepository checks;
    private final DeviceRepository devices;
    private final UserRepository users;
    private final SessionService sessions;
    private final ChecklistService checklist;
    private final Clock clock;

    public AccountService(AccountResolver accounts, SettingsService settingsService,
                          BalanceService balances, WeekService weeks, WeeklyCheckRepository checks,
                          DeviceRepository devices, UserRepository users,
                          SessionService sessions, ChecklistService checklist, Clock clock) {
        this.accounts = accounts;
        this.settingsService = settingsService;
        this.balances = balances;
        this.weeks = weeks;
        this.checks = checks;
        this.devices = devices;
        this.users = users;
        this.sessions = sessions;
        this.checklist = checklist;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountView current(AppPrincipal caller) {
        User account = accounts.resolve(caller);
        WeekCalendar calendar = settingsService.calendar();
        Instant now = clock.instant();
        LocalDate today = calendar.dayOf(now);
        ScreentimeRules rules = settingsService.rulesFor(today);
        LocalDate weekStart = calendar.weekStartOf(today);
        WeekState week = weeks.state(weekStart);

        List<Session> ofWeek = balances.sessionsOfWeek(account.getId(), weekStart, calendar);
        List<Booking> bookings = balances.toBookings(ofWeek, rules, now);
        int adjustmentMinutes = balances.adjustmentMinutes(weekStart);
        Balance balance = rules.balance(today, week, bookings, adjustmentMinutes);

        Map<Long, String> deviceNames = deviceNames();
        Map<Long, String> userNames = userNames();

        Session open = ofWeek.stream().filter(Session::isOpen).findFirst().orElse(null);
        OpenSessionView openView = null;
        if (open != null) {
            // The countdown runs from the start instant in the browser, so it
            // needs what was available before this session took anything.
            // Sending the balance with the session already in it would take
            // the elapsed time off twice after every reload.
            Balance withoutOpen = rules.balance(today, week, balances.toBookings(
                    ofWeek.stream().filter(s -> !s.isOpen()).toList(), rules, now), adjustmentMinutes);
            openView = new OpenSessionView(
                    open.getId(), open.getType(), open.getDeviceId(),
                    deviceNames.get(open.getDeviceId()), open.getStartedAt(),
                    calendar.secondsBetween(open.getStartedAt(), now),
                    open.getType() == SessionType.QUICK
                            ? withoutOpen.remainingQuickSeconds()
                            : withoutOpen.availableNowSeconds());
        }

        List<DayView> strip = dayStrip(today, week, rules, bookings);

        Map<Long, List<ChecklistTickView>> ticks = ticks(ofWeek);
        List<SessionView> todayEntries = ofWeek.stream()
                .filter(s -> calendar.dayOf(s.getStartedAt()).equals(today))
                .map(s -> toView(s, deviceNames, userNames, ticks, calendar, now))
                .toList();

        return new AccountView(
                account.getDisplayName(), today, weekStart,
                balance, holidayWeek(rules), week.bonusActive(),
                rules.settings().cutoffHour(),
                SettingsOverviewService.view(rules, week),
                !rules.beforeCutoff(now),
                openView,
                checklist.dueToday().stream().map(i -> new ChecklistDueView(i.id(), i.text())).toList(),
                strip, todayEntries,
                adjustmentViews(weekStart, userNames),
                devices.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream()
                        .map(d -> new DeviceView(d.getId(), d.getName(), d.isActive()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public WeekView week(AppPrincipal caller, LocalDate start) {
        User account = accounts.resolve(caller);
        WeekCalendar calendar = settingsService.calendar();
        Instant now = clock.instant();
        LocalDate today = calendar.dayOf(now);
        LocalDate weekStart = calendar.weekStartOf(start != null ? start : today);
        ScreentimeRules rules = settingsService.rulesFor(weekStart);
        WeekState week = weeks.state(weekStart);

        List<Session> ofWeek = balances.sessionsOfWeek(account.getId(), weekStart, calendar);
        List<Booking> bookings = balances.toBookings(ofWeek, rules, now);
        int adjustmentMinutes = balances.adjustmentMinutes(weekStart);

        // the balance of a week that is not the current one is reported against
        // its own last day, so a past week shows what it ended up at
        LocalDate referenceDay = weekStart.equals(calendar.weekStartOf(today)) ? today : weekStart.plusDays(6);
        Balance balance = rules.balance(referenceDay, week, bookings, adjustmentMinutes);

        Map<Long, String> deviceNames = deviceNames();
        Map<Long, String> userNames = userNames();

        List<DayDetailView> days = new ArrayList<>();
        Map<Long, List<ChecklistTickView>> ticks = ticks(ofWeek);
        for (LocalDate day : calendar.daysOfWeek(weekStart)) {
            Balance ofDay = rules.balance(day, week, bookings, adjustmentMinutes);
            List<SessionView> entries = ofWeek.stream()
                    .filter(s -> calendar.dayOf(s.getStartedAt()).equals(day))
                    .map(s -> toView(s, deviceNames, userNames, ticks, calendar, now))
                    .toList();
            days.add(new DayDetailView(day, day.getDayOfWeek(), ofDay.dailyCapSeconds(),
                    ofDay.dayUsedSeconds(), ofDay.remainingTodaySeconds(),
                    ofDay.quickUsedSeconds(), day.equals(today), day.isAfter(today), entries));
        }

        return new WeekView(weekStart, balance, holidayWeek(rules), week.bonusActive(), days,
                adjustmentViews(weekStart, userNames),
                checks.findByWeekStart(weekStart).map(c -> toCheckView(c, deviceNames)).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<WeekSummaryView> history(AppPrincipal caller, int weeksBack) {
        User account = accounts.resolve(caller);
        WeekCalendar calendar = settingsService.calendar();
        Instant now = clock.instant();
        LocalDate thisWeek = calendar.weekStartOf(calendar.dayOf(now));

        int count = Math.max(1, Math.min(weeksBack, 104));
        // each week is shown with the values that applied to it then
        SettingsService.RulesSource source = settingsService.rulesSource(thisWeek.minusWeeks(count - 1), thisWeek);
        List<WeekSummaryView> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDate weekStart = thisWeek.minusWeeks(i);
            ScreentimeRules rules = source.rulesFor(weekStart);
            WeekState week = weeks.state(weekStart);
            List<Booking> bookings = balances.toBookings(
                    balances.sessionsOfWeek(account.getId(), weekStart, calendar), rules, now);
            int adjustmentMinutes = balances.adjustmentMinutes(weekStart);
            Balance balance = rules.balance(weekStart.plusDays(6), week, bookings, adjustmentMinutes);
            rows.add(new WeekSummaryView(weekStart,
                    balance.weeklyBudgetSeconds(), balance.adjustmentSeconds(),
                    balance.weekUsedSeconds(), balance.remainingWeekSeconds(),
                    holidayWeek(rules), week.bonusActive(),
                    checks.findByWeekStart(weekStart).map(c -> toCheckView(c, deviceNames())).orElse(null)));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<SessionView> sessions(AppPrincipal caller, LocalDate from, LocalDate to) {
        WeekCalendar calendar = settingsService.calendar();
        Instant now = clock.instant();
        Map<Long, String> deviceNames = deviceNames();
        Map<Long, String> userNames = userNames();
        List<Session> listed = sessions.list(caller, from, to);
        Map<Long, List<ChecklistTickView>> ticks = ticks(listed);
        return listed.stream()
                .map(s -> toView(s, deviceNames, userNames, ticks, calendar, now))
                .toList();
    }

    /** The holiday set won for this week. A holiday day in a term week does not make it one. */
    private static boolean holidayWeek(ScreentimeRules rules) {
        return rules.week().scope() == SettingsScope.HOLIDAY;
    }

    private List<DayView> dayStrip(LocalDate today, WeekState week,
                                   ScreentimeRules rules, List<Booking> bookings) {
        List<DayView> strip = new ArrayList<>();
        for (LocalDate day : rules.calendar().daysOfWeek(week.weekStart())) {
            Balance ofDay = rules.balance(day, week, bookings, 0);
            strip.add(new DayView(day, day.getDayOfWeek(), ofDay.dailyCapSeconds(),
                    ofDay.dayUsedSeconds(), ofDay.remainingTodaySeconds(),
                    ofDay.quickUsedSeconds(), day.equals(today), day.isAfter(today)));
        }
        return strip;
    }

    private List<AdjustmentView> adjustmentViews(LocalDate weekStart, Map<Long, String> userNames) {
        return balances.adjustmentsOfWeek(weekStart).stream()
                .map(a -> new AdjustmentView(a.getId(), a.getWeekStart(), a.getMinutes(),
                        a.getReason(), userNames.get(a.getCreatedBy()), a.getCreatedAt(),
                        a.getCheckId() != null))
                .toList();
    }

    /** The checklist ticks of these sessions, in one read. */
    public Map<Long, List<ChecklistTickView>> ticks(List<Session> of) {
        return checklist.ticksFor(of.stream().map(Session::getId).toList());
    }

    public SessionView toView(Session s, Map<Long, String> deviceNames, Map<Long, String> userNames,
                              Map<Long, List<ChecklistTickView>> ticks, WeekCalendar calendar, Instant now) {
        return new SessionView(
                s.getId(), calendar.dayOf(s.getStartedAt()), s.getType(), s.getSource(),
                s.getDeviceId(), deviceNames.get(s.getDeviceId()),
                s.getStartedAt(), s.getEndedAt(),
                BalanceService.usedSeconds(s, calendar, now),
                s.isOpen(), s.isAutoClosed(), s.getNote(), userNames.get(s.getCreatedBy()),
                ticks.getOrDefault(s.getId(), List.of()));
    }

    public WeeklyCheckView toCheckView(WeeklyCheck c, Map<Long, String> deviceNames) {
        return new WeeklyCheckView(c.getId(), c.getWeekStart(), c.getLoggedMinutes(),
                c.getReportedMinutes(), c.getDifference(), c.getPenaltyMinutes(),
                c.isClean(), c.isDeliberate(),
                c.getBudgetMinutes(), c.getToleranceMinutes(), SettingsScope.valueOf(c.getSettingsScope()),
                c.getCheckedAt(),
                c.getReported().stream()
                        .map(r -> new ReportedDeviceView(r.getDeviceId(),
                                deviceNames.get(r.getDeviceId()), r.getMinutes()))
                        .toList());
    }

    public Map<Long, String> deviceNames() {
        return devices.findAll().stream()
                .collect(Collectors.toMap(Device::getId, Device::getName));
    }

    public Map<Long, String> userNames() {
        return users.findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName, (a, b) -> a));
    }
}

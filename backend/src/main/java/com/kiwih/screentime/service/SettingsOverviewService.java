package com.kiwih.screentime.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwih.screentime.domain.AuditLog;
import com.kiwih.screentime.domain.Setting;
import com.kiwih.screentime.domain.User;
import com.kiwih.screentime.repo.AuditLogRepository;
import com.kiwih.screentime.repo.UserRepository;
import com.kiwih.screentime.rules.*;
import com.kiwih.screentime.web.dto.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * What the settings screens show: the parent's overview of both value sets
 * with their history and change log, and, for everybody, the values in force
 * for a week together with the reason they are what they are.
 */
@Service
public class SettingsOverviewService {

    private static final int CHANGE_LOG_LENGTH = 100;

    private final SettingsService settings;
    private final WeekService weeks;
    private final UserRepository users;
    private final AuditLogRepository auditLog;
    private final ObjectMapper json;

    public SettingsOverviewService(SettingsService settings, WeekService weeks, UserRepository users,
                                   AuditLogRepository auditLog, ObjectMapper json) {
        this.settings = settings;
        this.weeks = weeks;
        this.users = users;
        this.auditLog = auditLog;
        this.json = json;
    }

    @PreAuthorize("hasRole('PARENT')")
    @Transactional(readOnly = true)
    public SettingsView overview() {
        LocalDate thisWeek = settings.calendar().weekStartOf(settings.today());
        LocalDate nextWeek = thisWeek.plusWeeks(1);
        SettingsHistory history = settings.history();
        List<Setting> rows = settings.rows();
        Map<Long, String> names = userNames();

        return new SettingsView(thisWeek, nextWeek,
                scope(SettingsScope.TERM, history, rows, thisWeek, nextWeek, names),
                scope(SettingsScope.HOLIDAY, history, rows, thisWeek, nextWeek, names),
                scope(SettingsScope.GLOBAL, history, rows, thisWeek, nextWeek, names),
                changes(rows, names));
    }

    /** The values in force for the week a day belongs to, and why. */
    @Transactional(readOnly = true)
    public EffectiveSettingsView effective(LocalDate anyDayOfTheWeek) {
        ScreentimeRules rules = settings.rulesFor(anyDayOfTheWeek);
        return view(rules, weeks.state(rules.week().weekStart()));
    }

    public static EffectiveSettingsView view(ScreentimeRules rules, WeekState state) {
        WeekSettings week = rules.week();
        List<EffectiveDayView> days = rules.calendar().daysOfWeek(week.weekStart()).stream()
                .map(day -> new EffectiveDayView(day, day.getDayOfWeek(), week.isHolidayDay(day),
                        rules.dailyCapMinutes(day, state)))
                .toList();
        return new EffectiveSettingsView(
                week.weekStart(), week.scope(), week.holidayDayCount(), week.holidayWeekThresholdDays(),
                week.holidayDays(),
                week.holidayNames().isEmpty() ? null : week.holidayNames().get(0),
                week.holidayNames(),
                state.bonusActive(),
                rules.weeklyBudgetMinutes(state),
                week.values().toIntMap(),
                days);
    }

    private static ScopeSettingsView scope(SettingsScope scope, SettingsHistory history, List<Setting> rows,
                                           LocalDate thisWeek, LocalDate nextWeek, Map<Long, String> names) {
        return new ScopeSettingsView(scope,
                new TreeMap<>(history.inForce(scope, thisWeek)),
                new TreeMap<>(history.inForce(scope, nextWeek)),
                rows.stream()
                        .filter(r -> r.getScope().equals(scope.name()))
                        .map(r -> new SettingRowView(r.getId(), r.getKey(), r.getValue(), r.getValidFrom(),
                                r.getCreatedBy() == null ? null : names.get(r.getCreatedBy()), r.getCreatedAt()))
                        .toList());
    }

    /**
     * Who changed what, when, and from which value to which, newest first.
     * Settings come from their own rows: the value before a row is the row
     * before it for the same key. Holiday periods have no history of their
     * own, so they come from the audit log.
     */
    private List<SettingChangeView> changes(List<Setting> rows, Map<Long, String> names) {
        List<SettingChangeView> changes = new ArrayList<>();

        Map<String, String> previous = new HashMap<>();
        for (Setting row : rows) {
            String id = row.getScope() + "/" + row.getKey();
            String before = previous.put(id, row.getValue());
            if (row.getCreatedBy() == null) {
                // a seeded default, not something anybody changed
                continue;
            }
            changes.add(new SettingChangeView(row.getCreatedAt(), names.get(row.getCreatedBy()),
                    "SETTING", "CHANGE", SettingsScope.valueOf(row.getScope()), row.getKey(),
                    before, row.getValue(), row.getValidFrom()));
        }

        for (AuditLog entry : auditLog.findTop100ByEntityOrderByAtDesc(AuditService.HOLIDAY_PERIOD)) {
            Map<String, String> before = read(entry.getOldValue());
            Map<String, String> after = read(entry.getNewValue());
            String name = after != null ? after.get("name") : before != null ? before.get("name") : null;
            changes.add(new SettingChangeView(entry.getAt(), names.get(entry.getUserId()),
                    "HOLIDAY_PERIOD", entry.getAction().name(), null, name,
                    describe(before), describe(after), null));
        }

        changes.sort(Comparator.comparing(SettingChangeView::at).reversed());
        return changes.size() > CHANGE_LOG_LENGTH ? changes.subList(0, CHANGE_LOG_LENGTH) : changes;
    }

    private Map<String, String> read(String value) {
        if (value == null) {
            return null;
        }
        try {
            return json.readValue(value, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String describe(Map<String, String> period) {
        if (period == null || period.isEmpty()) {
            return null;
        }
        return period.get("name") + ", " + period.get("startDate") + " to " + period.get("endDate");
    }

    private Map<Long, String> userNames() {
        return users.findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName, (a, b) -> a));
    }
}

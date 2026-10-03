package com.kiwih.screentime.service;

import com.kiwih.screentime.domain.HolidayPeriod;
import com.kiwih.screentime.domain.Setting;
import com.kiwih.screentime.repo.HolidayPeriodRepository;
import com.kiwih.screentime.repo.SettingRepository;
import com.kiwih.screentime.rules.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Reads the settings history and the holiday periods and hands out the rules
 * for a week. Settings are rows, never constants, so the rules are built per
 * request from whatever the tables say at that moment, and per week, because
 * two weeks can be governed by different values.
 */
@Service
public class SettingsService {

    private final SettingRepository settings;
    private final HolidayPeriodRepository holidays;
    private final WeekCalendar calendar;
    private final AuditService audit;
    private final Clock clock;
    private final SettingsChange change = new SettingsChange(new SettingsValidator());
    private final SettingsResolver resolver;

    public SettingsService(SettingRepository settings, HolidayPeriodRepository holidays,
                           WeekCalendar calendar, AuditService audit, Clock clock) {
        this.settings = settings;
        this.holidays = holidays;
        this.calendar = calendar;
        this.audit = audit;
        this.clock = clock;
        this.resolver = new SettingsResolver(calendar);
    }

    public WeekCalendar calendar() {
        return calendar;
    }

    public LocalDate today() {
        return calendar.dayOf(clock.instant());
    }

    @Transactional(readOnly = true)
    public SettingsHistory history() {
        return new SettingsHistory(settings.findAllByOrderByValidFromAscIdAsc().stream()
                .map(SettingsService::toValue)
                .toList());
    }

    /** The rules for the week a day belongs to. */
    @Transactional(readOnly = true)
    public ScreentimeRules rulesFor(LocalDate anyDayOfTheWeek) {
        return new ScreentimeRules(weekSettings(anyDayOfTheWeek), calendar);
    }

    /** The rules for the current week. */
    @Transactional(readOnly = true)
    public ScreentimeRules rules() {
        return rulesFor(today());
    }

    @Transactional(readOnly = true)
    public WeekSettings weekSettings(LocalDate anyDayOfTheWeek) {
        LocalDate monday = calendar.weekStartOf(anyDayOfTheWeek);
        return resolver.resolve(monday, history(), holidaysBetween(monday, monday.plusDays(6)));
    }

    /**
     * One read of both tables, for a screen that needs the rules of many
     * weeks, such as the history.
     */
    @Transactional(readOnly = true)
    public RulesSource rulesSource(LocalDate from, LocalDate to) {
        return new RulesSource(history(), holidaysBetween(calendar.weekStartOf(from),
                calendar.weekStartOf(to).plusDays(6)));
    }

    public final class RulesSource {
        private final SettingsHistory history;
        private final List<Holiday> periods;

        private RulesSource(SettingsHistory history, List<Holiday> periods) {
            this.history = history;
            this.periods = periods;
        }

        public ScreentimeRules rulesFor(LocalDate anyDayOfTheWeek) {
            return new ScreentimeRules(resolver.resolve(anyDayOfTheWeek, history, periods), calendar);
        }
    }

    @Transactional(readOnly = true)
    public List<Holiday> holidaysBetween(LocalDate from, LocalDate to) {
        return holidays.findOverlapping(from, to).stream().map(SettingsService::toHoliday).toList();
    }

    /**
     * Saves changed values of one scope, from this Monday or next Monday.
     * {@link SettingsChange} decides what that means and refuses anything that
     * does not hold together in every week it reaches. Nothing is overwritten:
     * every changed key gets a new row.
     */
    @PreAuthorize("hasRole('PARENT')")
    @Transactional
    public List<Setting> save(SettingsScope scope, Map<String, String> values, LocalDate validFrom, Long byUserId) {
        LocalDate monday = calendar.weekStartOf(today());
        SettingsHistory history = history();
        List<SettingValue> planned = change.plan(history, scope, values, validFrom, monday);

        List<Setting> saved = new java.util.ArrayList<>();
        for (SettingValue row : planned) {
            String previous = history.inForce(scope, row.validFrom()).get(row.key());
            Setting stored = settings.save(new Setting(scope.name(), row.key(), row.value(), row.validFrom(),
                    byUserId, clock.instant()));
            // the row is new, but for the family the setting changed, from
            // one value to another; the change log reads it that way
            audit.updated(AuditService.SETTING, stored.getId(),
                    Map.of("scope", scope.name(), "key", row.key(), "value", String.valueOf(previous)),
                    Map.of("scope", scope.name(), "key", row.key(), "value", row.value(),
                            "validFrom", row.validFrom().toString()),
                    byUserId);
            saved.add(stored);
        }
        return saved;
    }

    /** Every stored row, oldest first, for the settings page. */
    @Transactional(readOnly = true)
    public List<Setting> rows() {
        return settings.findAllByOrderByValidFromAscIdAsc();
    }

    private static SettingValue toValue(Setting s) {
        return new SettingValue(SettingsScope.valueOf(s.getScope()), s.getKey(), s.getValue(),
                s.getValidFrom(), s.getId());
    }

    static Holiday toHoliday(HolidayPeriod p) {
        return new Holiday(p.getId(), p.getName(), p.getStartDate(), p.getEndDate());
    }
}

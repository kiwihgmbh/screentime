package com.kiwih.screentime.rules;

import java.time.LocalDate;
import java.util.*;

/**
 * Turns a parent's save into the rows to write, or refuses it.
 *
 * A change starts this Monday or next Monday, both worked out by the server.
 * Nothing can start in the past, so last week's budget and a check already
 * made cannot move. A change is merged with the values in force and validated
 * as a whole, and not only for the week it starts: a value already planned for
 * a later week is still coming, and the combination has to hold then too.
 */
public final class SettingsChange {

    private static final Set<String> GLOBAL_KEYS = Set.of(SettingsValidator.HOLIDAY_WEEK_THRESHOLD_DAYS);

    private final SettingsValidator validator;

    public SettingsChange(SettingsValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /**
     * The rows to write, one per key whose value differs from the one in force
     * on the start Monday. Their sequence is {@link Long#MAX_VALUE}, newer than
     * anything stored; the database hands out the real one.
     *
     * @param validFrom     null for this Monday
     * @param currentMonday the Monday of the current week, from the server's clock
     */
    public List<SettingValue> plan(SettingsHistory history, SettingsScope scope, Map<String, String> values,
                                   LocalDate validFrom, LocalDate currentMonday) {
        LocalDate nextMonday = currentMonday.plusWeeks(1);
        LocalDate from = validFrom == null ? currentMonday : validFrom;
        if (!from.equals(currentMonday) && !from.equals(nextMonday)) {
            throw new RuleViolation(RuleViolation.Kind.INVALID,
                    "A change can apply from this week (" + currentMonday + ") or from next week ("
                            + nextMonday + "), not from " + from + ".");
        }

        Set<String> allowed = scope == SettingsScope.GLOBAL ? GLOBAL_KEYS : new HashSet<>(ScreentimeSettings.KEYS);
        Map<String, String> inForce = history.inForce(scope, from);
        List<SettingValue> rows = new ArrayList<>();
        for (var entry : new TreeMap<>(values).entrySet()) {
            String key = entry.getKey();
            if (!allowed.contains(key)) {
                throw new RuleViolation(RuleViolation.Kind.INVALID,
                        "There is no " + scope.label().toLowerCase(Locale.ROOT) + " setting called " + key + ".");
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!value.equals(inForce.get(key))) {
                rows.add(new SettingValue(scope, key, value, from, Long.MAX_VALUE));
            }
        }

        // every Monday on which the values of this scope change from here on
        SortedSet<LocalDate> mondays = new TreeSet<>();
        mondays.add(from);
        history.rows().stream()
                .filter(r -> r.scope() == scope && r.validFrom().isAfter(from))
                .forEach(r -> mondays.add(r.validFrom()));

        List<SettingValue> after = new ArrayList<>(history.rows());
        after.addAll(rows);
        SettingsHistory planned = new SettingsHistory(after);

        List<SettingsProblem> problems = new ArrayList<>();
        for (LocalDate monday : mondays) {
            for (SettingsProblem p : validate(planned, scope, monday)) {
                problems.add(monday.equals(from) ? p : new SettingsProblem(p.rule(), p.keys(),
                        "From the week of " + monday + ", " + lowerFirst(p.message())));
            }
        }
        if (!problems.isEmpty()) {
            throw new InvalidSettingsException(problems);
        }
        return rows;
    }

    private List<SettingsProblem> validate(SettingsHistory history, SettingsScope scope, LocalDate monday) {
        if (scope == SettingsScope.GLOBAL) {
            String raw = history.inForce(scope, monday).get(SettingsValidator.HOLIDAY_WEEK_THRESHOLD_DAYS);
            int days;
            try {
                days = Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                throw new RuleViolation(RuleViolation.Kind.INVALID, SettingsValidator.HOLIDAY_WEEK_THRESHOLD_DAYS
                        + " must be a whole number of days but was \"" + raw + "\".");
            }
            return validator.validateHolidayWeekThreshold(days);
        }
        ScreentimeSettings values;
        try {
            values = history.values(scope, monday);
        } catch (IllegalArgumentException e) {
            // a value that is not a number, named by ScreentimeSettings
            throw new RuleViolation(RuleViolation.Kind.INVALID, e.getMessage());
        }
        return validator.validate(scope, values);
    }

    private static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}

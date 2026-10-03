package com.kiwih.screentime;

import com.kiwih.screentime.repo.*;
import com.kiwih.screentime.rules.ScreentimeSettings;
import com.kiwih.screentime.rules.SettingsScope;
import com.kiwih.screentime.rules.SettingsValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the migrations apply to an empty PostgreSQL 16, that the entities
 * match the schema Flyway produced (ddl-auto is validate), and that the
 * invariants the schema is supposed to guarantee really are enforced.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MigrationTest extends PostgresTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UserRepository users;

    @Autowired
    DeviceRepository devices;

    @Test
    void migrationsApplyAndEntitiesMatchTheSchema() {
        // reaching this point means Hibernate validated every entity against
        // the migrated schema during context startup
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(applied).isEqualTo(6);
    }

    @Test
    void devicesAreSeeded() {
        assertThat(devices.findAllByOrderBySortOrderAscNameAsc())
                .extracting(d -> d.getName())
                .containsExactly("iPad", "iMac", "Phone", "PlayStation", "TV");
    }

    @Test
    void bothValueSetsAndTheThresholdAreSeededWithTheDocumentedDefaults() {
        assertThat(seeded("TERM")).isEqualTo(ScreentimeSettings.TERM_DEFAULTS.toMap());
        assertThat(seeded("HOLIDAY")).isEqualTo(ScreentimeSettings.HOLIDAY_DEFAULTS.toMap());
        assertThat(seeded("GLOBAL")).containsExactly(Map.entry("holidayWeekThresholdDays", "4"));
    }

    @Test
    void theSeededValuesParseAndHoldTogether() {
        // drift between the migration and ScreentimeSettings would mean the
        // application starts and then fails the first time a balance is read
        SettingsValidator validator = new SettingsValidator();
        assertThat(validator.validate(SettingsScope.TERM, ScreentimeSettings.fromMap(seeded("TERM")))).isEmpty();
        assertThat(validator.validate(SettingsScope.HOLIDAY, ScreentimeSettings.fromMap(seeded("HOLIDAY")))).isEmpty();
    }

    @Test
    void theSeededValuesAreInForceForEveryWeekTheAppCanShow() {
        assertThat(jdbc.queryForList("select distinct valid_from::text from settings", String.class))
                .containsExactly("2000-01-03");
        assertThat(jdbc.queryForList("select distinct created_by from settings", Long.class))
                .as("seeded, not created by anybody")
                .containsExactly((Long) null);
    }

    @Test
    void aSettingsRowCannotBeChangedInPlace() {
        assertThatThrownBy(() -> jdbc.update(
                "update settings set value = '400' where scope = 'TERM' and key = 'weeklyMinutes'"))
                .hasMessageContaining("never changed in place");
    }

    @Test
    void aSettingCanOnlyStartOnAMonday() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into settings (scope, key, value, valid_from)
                values ('TERM', 'weeklyMinutes', '400', date '2026-10-07')
                """))
                .hasMessageContaining("ck_settings_valid_from_monday");
    }

    @Test
    void holidayPeriodsCannotShareADay() {
        insertHoliday("Autumn holidays", "2026-10-10", "2026-10-25");

        assertThatThrownBy(() -> insertHoliday("Camp", "2026-10-25", "2026-10-28"))
                .as("the last day of one period is the first of the other")
                .hasMessageContaining("ex_holiday_periods_overlap");
    }

    @Test
    void holidayPeriodsThatOnlyTouchAreFine() {
        insertHoliday("Autumn holidays", "2026-10-10", "2026-10-25");
        insertHoliday("After", "2026-10-26", "2026-10-28");
        assertThat(jdbc.queryForObject("select count(*) from holiday_periods", Integer.class)).isEqualTo(2);
    }

    @Test
    void aHolidayPeriodCannotEndBeforeItStarts() {
        assertThatThrownBy(() -> insertHoliday("Backwards", "2026-10-25", "2026-10-10"))
                .hasMessageContaining("ck_holiday_periods_order");
    }

    @Test
    void noChecklistItemsAreSeeded() {
        assertThat(jdbc.queryForObject("select count(*) from checklist_items", Integer.class)).isZero();
    }

    @Test
    void aChecklistItemOnlyNamesRealWeekdays() {
        long parent = insertUser("parent", "PARENT");
        insertChecklistItem(parent, "MONDAY,SATURDAY");
        assertThatThrownBy(() -> insertChecklistItem(parent, "MONDAY,FUNDAY"))
                .hasMessageContaining("ck_checklist_items_weekdays");
    }

    @Test
    void aChecklistItemCannotEndBeforeItStarts() {
        long parent = insertUser("parent", "PARENT");
        assertThatThrownBy(() -> jdbc.update("""
                insert into checklist_items (text, valid_from, valid_until, created_by)
                values ('Backwards', date '2026-10-09', date '2026-10-05', ?)
                """, parent))
                .hasMessageContaining("ck_checklist_items_range");
    }

    @Test
    void anItemIsTickedAtMostOncePerStart() {
        long parent = insertUser("parent", "PARENT");
        long child = insertUser("child", "CHILD");
        long item = insertChecklistItem(parent, null);
        long deviceId = jdbc.queryForObject("select id from devices where name = 'iPad'", Long.class);
        insertOpenSession(child, deviceId);
        long session = jdbc.queryForObject("select id from sessions where user_id = ?", Long.class, child);

        String tick = """
                insert into checklist_ticks (session_id, item_id, item_text, user_id, ticked_at)
                values (?, ?, 'Homework', ?, now())
                """;
        jdbc.update(tick, session, item, child);
        assertThatThrownBy(() -> jdbc.update(tick, session, item, child))
                .hasMessageContaining("uq_checklist_ticks_session_item");
    }

    @Test
    void noHolidayPeriodsAreSeeded() {
        assertThat(jdbc.queryForObject("select count(*) from holiday_periods", Integer.class)).isZero();
    }

    @Test
    void noUsersAreSeeded() {
        assertThat(users.findAll()).isEmpty();
    }

    @Test
    void instantColumnsAreTimestampWithTimeZone() {
        List<String> plain = jdbc.queryForList("""
                select table_name || '.' || column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name <> 'flyway_schema_history'
                  and data_type = 'timestamp without time zone'
                """, String.class);
        assertThat(plain)
                .as("every instant must be timestamptz so no reader has to guess a zone")
                .isEmpty();
    }

    @Test
    void onlyWeekAndDayColumnsArePlainDates() {
        List<String> dates = jdbc.queryForList("""
                select table_name || '.' || column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name <> 'flyway_schema_history'
                  and data_type = 'date'
                order by 1
                """, String.class);
        assertThat(dates).containsExactly(
                "adjustments.week_start",
                "checklist_items.valid_from",
                "checklist_items.valid_until",
                "holiday_periods.end_date",
                "holiday_periods.start_date",
                "settings.valid_from",
                "week_flags.week_start",
                "weekly_checks.week_start");
    }

    @Test
    void aUserCanHaveOnlyOneOpenSession() {
        long userId = insertUser("child", "CHILD");
        long deviceId = jdbc.queryForObject("select id from devices where name = 'iPad'", Long.class);

        insertOpenSession(userId, deviceId);

        assertThatThrownBy(() -> insertOpenSession(userId, deviceId))
                .hasMessageContaining("uq_sessions_one_open_per_user");
    }

    @Test
    void aClosedSessionWithoutADurationIsRejected() {
        long userId = insertUser("child2", "CHILD");
        long deviceId = jdbc.queryForObject("select id from devices where name = 'iPad'", Long.class);

        assertThatThrownBy(() -> jdbc.update("""
                insert into sessions (user_id, started_at, ended_at, duration_seconds, device_id, type, source, created_by)
                values (?, now(), now(), null, ?, 'FUN', 'TIMER', ?)
                """, userId, deviceId, userId))
                .hasMessageContaining("ck_sessions_closed");
    }

    @Test
    void aManualEntryMustBeClosed() {
        long userId = insertUser("child3", "CHILD");
        long deviceId = jdbc.queryForObject("select id from devices where name = 'iPad'", Long.class);

        assertThatThrownBy(() -> jdbc.update("""
                insert into sessions (user_id, started_at, device_id, type, source, created_by)
                values (?, now(), ?, 'FUN', 'MANUAL', ?)
                """, userId, deviceId, userId))
                .hasMessageContaining("ck_sessions_manual_closed");
    }

    @Test
    void aWeekHasAtMostOneCheck() {
        long userId = insertUser("parent", "PARENT");
        insertCheck(userId);

        assertThatThrownBy(() -> insertCheck(userId))
                .hasMessageContaining("uq_weekly_checks_week");
    }

    private Map<String, String> seeded(String scope) {
        Map<String, String> values = new java.util.HashMap<>();
        jdbc.query("select key, value from settings where scope = ?",
                rs -> { values.put(rs.getString(1), rs.getString(2)); }, scope);
        return values;
    }

    private long insertChecklistItem(long createdBy, String weekdays) {
        return jdbc.queryForObject("""
                insert into checklist_items (text, weekdays, created_by) values ('Homework', ?, ?) returning id
                """, Long.class, weekdays, createdBy);
    }

    private void insertHoliday(String name, String from, String to) {
        jdbc.update("insert into holiday_periods (name, start_date, end_date) values (?, ?::date, ?::date)",
                name, from, to);
    }

    private long insertUser(String username, String role) {
        // a BCrypt shaped placeholder, not a usable credential
        return jdbc.queryForObject("""
                insert into users (username, password_hash, display_name, role)
                values (?, '$2a$10$0000000000000000000000000000000000000000000000000000', ?, ?)
                returning id
                """, Long.class, username, username, role);
    }

    private void insertOpenSession(long userId, long deviceId) {
        jdbc.update("""
                insert into sessions (user_id, started_at, device_id, type, source, created_by)
                values (?, now(), ?, 'FUN', 'TIMER', ?)
                """, userId, deviceId, userId);
    }

    private void insertCheck(long userId) {
        jdbc.update("""
                insert into weekly_checks (week_start, logged_minutes, reported_minutes, difference,
                                           penalty_minutes, clean, deliberate, budget_minutes,
                                           tolerance_minutes, settings_scope, checked_by)
                values (date '2026-09-28', 400, 405, 5, 0, true, false, 480, 10, 'TERM', ?)
                """, userId);
    }
}

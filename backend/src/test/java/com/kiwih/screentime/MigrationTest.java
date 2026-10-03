package com.kiwih.screentime;

import com.kiwih.screentime.domain.Setting;
import com.kiwih.screentime.repo.*;
import com.kiwih.screentime.rules.ScreentimeSettings;
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

    @Autowired
    SettingRepository settings;

    @Test
    void migrationsApplyAndEntitiesMatchTheSchema() {
        // reaching this point means Hibernate validated every entity against
        // the migrated schema during context startup
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(applied).isEqualTo(3);
    }

    @Test
    void devicesAreSeeded() {
        assertThat(devices.findAllByOrderBySortOrderAscNameAsc())
                .extracting(d -> d.getName())
                .containsExactly("iPad", "iMac", "Phone", "PlayStation", "TV");
    }

    @Test
    void settingsAreSeededWithTheDocumentedDefaults() {
        assertThat(settings.findAll()).hasSize(11);
        assertThat(settings.findById("weeklyMinutes")).get()
                .extracting(s -> s.getValue()).isEqualTo("480");
        assertThat(settings.findById("weekdayCapMinutes")).get()
                .extracting(s -> s.getValue()).isEqualTo("60");
        assertThat(settings.findById("weekendCapMinutes")).get()
                .extracting(s -> s.getValue()).isEqualTo("120");
        assertThat(settings.findById("quickDailyMinutes")).get()
                .extracting(s -> s.getValue()).isEqualTo("15");
        assertThat(settings.findById("cutoffHour")).get()
                .extracting(s -> s.getValue()).isEqualTo("20");
        assertThat(settings.findById("bonusMinutes")).get()
                .extracting(s -> s.getValue()).isEqualTo("60");
        assertThat(settings.findById("bonusWeekendCapMinutes")).get()
                .extracting(s -> s.getValue()).isEqualTo("150");
        assertThat(settings.findById("maxPenaltyMinutes")).get()
                .extracting(s -> s.getValue()).isEqualTo("120");
        assertThat(settings.findById("toleranceMinutes")).get()
                .extracting(s -> s.getValue()).isEqualTo("10");
        assertThat(settings.findById("manualMaxMinutes")).get()
                .extracting(s -> s.getValue()).isEqualTo("240");
        assertThat(settings.findById("deliberatePenaltyMinutes")).get()
                .extracting(s -> s.getValue()).isEqualTo("60");
    }

    @Test
    void theSettingsTableHoldsExactlyTheKeysTheRulesReadAndTheSeededValuesAreValid() {
        // drift between the migration and ScreentimeSettings would mean the
        // application starts and then fails the first time a balance is read
        Map<String, String> seeded = settings.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Setting::getKey, Setting::getValue));

        assertThat(seeded.keySet())
                .containsExactlyInAnyOrderElementsOf(ScreentimeSettings.KEYS);
        assertThat(ScreentimeSettings.fromMap(seeded))
                .as("the seeded rows parse and satisfy every settings invariant")
                .isEqualTo(ScreentimeSettings.DEFAULTS);
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
    void aClosedSessionWithoutMinutesIsRejected() {
        long userId = insertUser("child2", "CHILD");
        long deviceId = jdbc.queryForObject("select id from devices where name = 'iPad'", Long.class);

        assertThatThrownBy(() -> jdbc.update("""
                insert into sessions (user_id, started_at, ended_at, minutes, device_id, type, source, created_by)
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
                                           penalty_minutes, clean, deliberate, checked_by)
                values (date '2026-09-28', 400, 405, 5, 0, true, false, ?)
                """, userId);
    }
}

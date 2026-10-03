package com.kiwih.screentime;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5 runs on a database that is already in use: settings a parent changed,
 * weeks flagged as holiday weeks, checks already made. This brings a fresh
 * schema to V4, puts that kind of data in, and migrates the rest of the way.
 */
class HolidayMigrationTest extends PostgresTestBase {

    @Test
    void whatTheFamilyAlreadyHasIsCarriedOver() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        String schema = "v5_" + System.nanoTime();
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        Flyway.configure().dataSource(ds).schemas(schema).target("4").load().migrate();

        // a BCrypt shaped placeholder, not a usable credential
        Long parent = jdbc.queryForObject("insert into " + schema + ".users "
                + "(username, password_hash, display_name, role) values "
                + "('parent', '$2a$10$0000000000000000000000000000000000000000000000000000', 'parent', 'PARENT') "
                + "returning id", Long.class);
        jdbc.update("update " + schema + ".settings set value = '420', updated_by = ? where key = 'weeklyMinutes'",
                parent);
        jdbc.update("insert into " + schema + ".week_flags (week_start, holiday, bonus_active) values "
                + "(date '2026-10-12', true, false), (date '2026-09-28', false, true)");
        jdbc.update("insert into " + schema + ".weekly_checks (week_start, logged_minutes, reported_minutes, "
                + "difference, penalty_minutes, clean, deliberate, checked_by) values "
                + "(date '2026-09-28', 400, 405, 5, 0, true, false, ?)", parent);

        Flyway.configure().dataSource(ds).schemas(schema).load().migrate();

        Map<String, Object> term = jdbc.queryForMap("select value, valid_from::text as valid_from, created_by from "
                + schema + ".settings where scope = 'TERM' and key = 'weeklyMinutes'");
        assertThat(term.get("value")).as("the family's own value, not the default").isEqualTo("420");
        assertThat(term.get("valid_from")).isEqualTo("2000-01-03");
        assertThat(term.get("created_by")).isEqualTo(parent);

        assertThat(jdbc.queryForObject("select value from " + schema
                + ".settings where scope = 'HOLIDAY' and key = 'weeklyMinutes'", String.class)).isEqualTo("720");
        assertThat(jdbc.queryForObject("select value from " + schema
                + ".settings where scope = 'GLOBAL' and key = 'holidayWeekThresholdDays'", String.class)).isEqualTo("4");

        Map<String, Object> period = jdbc.queryForMap("select name, start_date::text as s, end_date::text as e from "
                + schema + ".holiday_periods");
        assertThat(period).containsEntry("name", "Holiday week")
                .containsEntry("s", "2026-10-12")
                .containsEntry("e", "2026-10-18");

        assertThat(jdbc.queryForObject("select bonus_active from " + schema
                + ".week_flags where week_start = date '2026-09-28'", Boolean.class))
                .as("the bonus switch stays where it was")
                .isTrue();

        Map<String, Object> check = jdbc.queryForMap("select budget_minutes, tolerance_minutes, settings_scope from "
                + schema + ".weekly_checks");
        assertThat(check.get("budget_minutes"))
                .as("420 plus the 60 minute bonus that week had")
                .isEqualTo(480);
        assertThat(check.get("tolerance_minutes")).isEqualTo(10);
        assertThat(check.get("settings_scope")).isEqualTo("TERM");
    }
}

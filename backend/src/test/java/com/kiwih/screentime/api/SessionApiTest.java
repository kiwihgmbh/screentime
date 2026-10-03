package com.kiwih.screentime.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("starting, stopping, booking and correcting through the API")
class SessionApiTest extends ApiTestBase {

    @Nested
    @DisplayName("the server decides every timestamp")
    class ServerTime {

        @Test
        void aStartTimeComesFromTheServerAndNotFromTheRequest() throws Exception {
            // the request carries a start time far in the past; it must be ignored
            Map<String, Object> body = new HashMap<>();
            body.put("deviceId", deviceId("iPad"));
            body.put("type", "FUN");
            body.put("startedAt", "2020-01-01T00:00:00Z");
            body.put("minutes", 9999);

            JsonNode started = readBody(asChild(post("/api/sessions/start"), body)
                    .andExpect(status().isCreated()));

            assertThat(Instant.parse(started.get("startedAt").asText()))
                    .isEqualTo(time.instant());
            assertThat(started.get("day").asText()).isEqualTo(FRIDAY.toString());
        }

        @Test
        void theDurationIsTheTimeThatActuallyPassed() throws Exception {
            asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("iPad"), "type", "FUN"))
                    .andExpect(status().isCreated());

            time.advanceMinutes(25);
            JsonNode stopped = readBody(asChild(post("/api/sessions/stop")).andExpect(status().isOk()));

            assertThat(stopped.get("seconds").asInt()).isEqualTo(25 * 60);
            assertThat(stopped.get("running").asBoolean()).isFalse();
            assertThat(stopped.get("autoClosed").asBoolean()).isFalse();
        }

        @Test
        void aSessionShorterThanAMinuteCostsItsSeconds() throws Exception {
            asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("iPad"), "type", "FUN"))
                    .andExpect(status().isCreated());

            time.advanceSeconds(40);
            JsonNode stopped = readBody(asChild(post("/api/sessions/stop")).andExpect(status().isOk()));

            assertThat(stopped.get("seconds").asInt()).isEqualTo(40);
            assertThat(currentAsChild().get("balance").get("remainingTodaySeconds").asInt())
                    .as("40 seconds used to round down to nothing")
                    .isEqualTo(60 * 60 - 40);
        }

        @Test
        void aRunningSessionReportsItsSecondsSoFar() throws Exception {
            asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("iPad"), "type", "FUN"));
            time.advanceSeconds(95);

            JsonNode current = currentAsChild();
            assertThat(current.get("openSession").get("elapsedSeconds").asInt()).isEqualTo(95);
            assertThat(current.get("openSession").get("countdownAgainstSeconds").asInt())
                    .as("available now without the running session; the browser takes the elapsed time off")
                    .isEqualTo(60 * 60);
        }

        @Test
        void aRunningSessionAlreadyCountsAgainstTheBudget() throws Exception {
            asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("iPad"), "type", "FUN"));
            time.advanceMinutes(20);

            JsonNode current = currentAsChild();
            assertThat(current.get("openSession").get("elapsedSeconds").asInt()).isEqualTo(20 * 60);
            assertThat(current.get("balance").get("dayUsedSeconds").asInt())
                    .as("the countdown has to be honest while the timer runs")
                    .isEqualTo(20 * 60);
            assertThat(current.get("balance").get("remainingTodaySeconds").asInt()).isEqualTo(40 * 60);
        }
    }

    @Nested
    @DisplayName("one session at a time")
    class OneAtATime {

        @Test
        void aSecondStartIsRefusedAndNamesTheRunningSession() throws Exception {
            Long first = readBody(asChild(post("/api/sessions/start"),
                    Map.of("deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();

            JsonNode error = readBody(asChild(post("/api/sessions/start"),
                    Map.of("deviceId", deviceId("TV"), "type", "FUN"))
                    .andExpect(status().isConflict()));

            assertThat(error.get("details").get("openSessionId").asLong()).isEqualTo(first);
        }

        @Test
        void stoppingWithNothingRunningSaysSoRatherThanFailing() throws Exception {
            asChild(post("/api/sessions/stop")).andExpect(status().isBadRequest());
        }

        @Test
        void afterStoppingAnotherSessionCanStart() throws Exception {
            asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("iPad"), "type", "FUN"));
            time.advanceMinutes(5);
            asChild(post("/api/sessions/stop")).andExpect(status().isOk());
            asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("TV"), "type", "FUN"))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("the cut off, through the API")
    class CutOff {

        @Test
        void aChildCannotStartAtTheCutOffHour() throws Exception {
            time.setLocal(FRIDAY, 20, 0);
            asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("iPad"), "type", "FUN"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void oneMinuteBeforeIsStillFine() throws Exception {
            time.setLocal(FRIDAY, 19, 59);
            asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("iPad"), "type", "FUN"))
                    .andExpect(status().isCreated());
        }

        @Test
        void aParentCanStartTheFilmAfterTheCutOff() throws Exception {
            time.setLocal(FRIDAY, 20, 30);
            asParent(post("/api/sessions/start"), Map.of("deviceId", deviceId("TV"), "type", "FILM"))
                    .andExpect(status().isCreated());
        }

        @Test
        void aChildCannotStartAFilm() throws Exception {
            asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("TV"), "type", "FILM"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void theDashboardSaysWhenScreensAreOff() throws Exception {
            assertThat(currentAsChild().get("screensOff").asBoolean()).isFalse();
            time.setLocal(FRIDAY, 20, 0);
            assertThat(currentAsChild().get("screensOff").asBoolean()).isTrue();
        }
    }

    @Nested
    @DisplayName("what a child may book by hand")
    class ManualBooking {

        @Test
        void aChildCanBookToday() throws Exception {
            JsonNode booked = readBody(asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 30, "deviceId", deviceId("iPad"), "type", "FUN"))
                    .andExpect(status().isCreated()));
            assertThat(booked.get("day").asText()).isEqualTo(FRIDAY.toString());
            assertThat(booked.get("seconds").asInt()).isEqualTo(30 * 60);
            assertThat(booked.get("source").asText()).isEqualTo("MANUAL");
        }

        @Test
        void aChildSendingYesterdaysDateIsRefusedWithForbidden() throws Exception {
            asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 30, "deviceId", deviceId("iPad"), "type", "FUN",
                    "date", FRIDAY.minusDays(1).toString()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aChildSendingTomorrowsDateIsRefusedWithForbidden() throws Exception {
            asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 30, "deviceId", deviceId("iPad"), "type", "FUN",
                    "date", FRIDAY.plusDays(1).toString()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aChildSendingTodaysDateExplicitlyIsFine() throws Exception {
            asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 30, "deviceId", deviceId("iPad"), "type", "FUN",
                    "date", FRIDAY.toString()))
                    .andExpect(status().isCreated());
        }

        @Test
        void aChildCannotBookAfterTheCutOff() throws Exception {
            time.setLocal(FRIDAY, 20, 5);
            asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 30, "deviceId", deviceId("iPad"), "type", "FUN"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aChildCannotBookMoreThanTheSingleEntryLimit() throws Exception {
            asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 240, "deviceId", deviceId("iPad"), "type", "FUN"))
                    .andExpect(status().isCreated());
            asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 241, "deviceId", deviceId("iPad"), "type", "FUN"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aParentCanBookAPastDayAfterTheCutOff() throws Exception {
            time.setLocal(FRIDAY, 22, 0);
            JsonNode booked = readBody(asParent(post("/api/sessions/manual"), Map.of(
                    "minutes", 45, "deviceId", deviceId("iMac"), "type", "FUN",
                    "date", MONDAY.toString(), "note", "forgot to log it"))
                    .andExpect(status().isCreated()));

            assertThat(booked.get("day").asText()).isEqualTo(MONDAY.toString());
            assertThat(booked.get("createdBy").asText()).isEqualTo(PARENT_USERNAME);
            assertThat(booked.get("note").asText()).isEqualTo("forgot to log it");
        }

        @Test
        void aParentsEntryBelongsToTheChildsAccount() throws Exception {
            asParent(post("/api/sessions/manual"), Map.of(
                    "minutes", 45, "deviceId", deviceId("iMac"), "type", "FUN"))
                    .andExpect(status().isCreated());

            assertThat(currentAsChild().get("balance").get("weekUsedSeconds").asInt())
                    .as("a parent has no screen time of their own to account for")
                    .isEqualTo(45 * 60);
        }

        @Test
        void zeroMinutesIsRefused() throws Exception {
            asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 0, "deviceId", deviceId("iPad"), "type", "FUN"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("the budget is visible and adds up")
    class Budget {

        @Test
        void quickTimeDoesNotTouchTheWeeklyBudget() throws Exception {
            asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 10, "deviceId", deviceId("iPad"), "type", "QUICK"))
                    .andExpect(status().isCreated());

            JsonNode balance = currentAsChild().get("balance");
            assertThat(balance.get("weekUsedSeconds").asInt()).isZero();
            assertThat(balance.get("remainingWeekSeconds").asInt()).isEqualTo(480 * 60);
            assertThat(balance.get("quickUsedSeconds").asInt()).isEqualTo(10 * 60);
            assertThat(balance.get("remainingQuickSeconds").asInt()).isEqualTo(5 * 60);
        }

        @Test
        void theFilmCountsAgainstNothing() throws Exception {
            time.setLocal(FRIDAY, 20, 30);
            asParent(post("/api/sessions/manual"), Map.of(
                    "minutes", 110, "deviceId", deviceId("TV"), "type", "FILM"))
                    .andExpect(status().isCreated());

            JsonNode balance = currentAsChild().get("balance");
            assertThat(balance.get("weekUsedSeconds").asInt()).isZero();
            assertThat(balance.get("dayUsedSeconds").asInt()).isZero();
            assertThat(balance.get("quickUsedSeconds").asInt()).isZero();
        }

        @Test
        void theDashboardCarriesTheSevenDayStripWithTodayMarked() throws Exception {
            JsonNode current = currentAsChild();
            JsonNode week = current.get("week");
            assertThat(week).hasSize(7);
            assertThat(week.get(0).get("date").asText()).isEqualTo(MONDAY.toString());
            assertThat(week.get(6).get("date").asText()).isEqualTo(SUNDAY.toString());
            assertThat(week.get(4).get("today").asBoolean()).as("Friday is today").isTrue();
            assertThat(week.get(4).get("capSeconds").asInt()).isEqualTo(60 * 60);
            assertThat(week.get(5).get("capSeconds").asInt()).as("Saturday").isEqualTo(120 * 60);
            assertThat(week.get(5).get("future").asBoolean()).isTrue();
        }

        @Test
        void theDashboardShowsTheArithmeticAndNotJustTheAnswer() throws Exception {
            asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 20, "deviceId", deviceId("iPad"), "type", "FUN"));
            asParent(post("/api/adjustments"), Map.of(
                    "weekStart", MONDAY.toString(), "minutes", -30, "reason", "bike left outside"))
                    .andExpect(status().isCreated());

            JsonNode current = currentAsChild();
            JsonNode balance = current.get("balance");
            assertThat(balance.get("weeklyBudgetSeconds").asInt()).isEqualTo(480 * 60);
            assertThat(balance.get("adjustmentSeconds").asInt()).isEqualTo(-30 * 60);
            assertThat(balance.get("weekUsedSeconds").asInt()).isEqualTo(20 * 60);
            assertThat(balance.get("remainingWeekSeconds").asInt()).isEqualTo(430 * 60);
            assertThat(current.get("weekAdjustments").get(0).get("reason").asText())
                    .as("a number that drops must come with a reason the child can read")
                    .isEqualTo("bike left outside");
        }

        @Test
        void aWeekInTheHolidaysGetsTheHolidayValues() throws Exception {
            holiday("Autumn holidays", MONDAY.minusDays(2), SUNDAY.plusDays(7));

            JsonNode current = currentAsChild();
            assertThat(current.get("holidayWeek").asBoolean()).isTrue();
            assertThat(current.get("balance").get("weeklyBudgetSeconds").asInt()).isEqualTo(720 * 60);
            assertThat(current.get("balance").get("dailyCapSeconds").asInt())
                    .as("Friday in the holidays gets the holiday weekday ceiling")
                    .isEqualTo(120 * 60);
            assertThat(current.get("cutoffHour").asInt()).isEqualTo(21);
        }

        @Test
        void holidaysFromSaturdayLeaveTheWeekOnTermValues() throws Exception {
            holiday("Autumn holidays", SATURDAY, SATURDAY.plusDays(15));
            // written by the test parent, so the reset removes it again
            jdbc.update("""
                    insert into settings (scope, key, value, valid_from, created_by)
                    values ('HOLIDAY', 'weekendCapMinutes', '140', date '2000-01-03',
                            (select id from users where username = ?))
                    """, PARENT_USERNAME);

            JsonNode current = currentAsChild();
            assertThat(current.get("holidayWeek").asBoolean())
                    .as("two holiday days of seven")
                    .isFalse();
            assertThat(current.get("balance").get("weeklyBudgetSeconds").asInt()).isEqualTo(480 * 60);
            assertThat(current.get("balance").get("dailyCapSeconds").asInt())
                    .as("Friday is still a school day")
                    .isEqualTo(60 * 60);
            assertThat(current.get("week").get(5).get("capSeconds").asInt())
                    .as("Saturday is a holiday day and gets the holiday weekend ceiling")
                    .isEqualTo(140 * 60);
        }

        @Test
        void availableNowIsTheSmallerOfTheWeekAndTheDay() throws Exception {
            // use up most of the week on earlier days
            for (int day = 0; day < 4; day++) {
                asParent(post("/api/sessions/manual"), Map.of(
                        "minutes", 60, "deviceId", deviceId("iPad"), "type", "FUN",
                        "date", MONDAY.plusDays(day).toString()));
            }
            asParent(post("/api/sessions/manual"), Map.of(
                    "minutes", 215, "deviceId", deviceId("iPad"), "type", "FUN",
                    "date", MONDAY.toString()));

            JsonNode balance = currentAsChild().get("balance");
            assertThat(balance.get("weekUsedSeconds").asInt()).isEqualTo(455 * 60);
            assertThat(balance.get("remainingWeekSeconds").asInt()).isEqualTo(25 * 60);
            assertThat(balance.get("remainingTodaySeconds").asInt()).isEqualTo(60 * 60);
            assertThat(balance.get("availableNowSeconds").asInt()).isEqualTo(25 * 60);
        }
    }

    @Nested
    @DisplayName("a parent's corrections")
    class Corrections {

        @Test
        void aParentCanCorrectTheMinutesOfAnEntry() throws Exception {
            Long id = readBody(asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 120, "deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();

            JsonNode corrected = readBody(asParent(put("/api/sessions/" + id), Map.of("minutes", 30))
                    .andExpect(status().isOk()));
            assertThat(corrected.get("seconds").asInt()).isEqualTo(30 * 60);
            assertThat(currentAsChild().get("balance").get("weekUsedSeconds").asInt()).isEqualTo(30 * 60);
        }

        @Test
        void aCorrectionThatDoesNotTouchTheDurationKeepsItsSeconds() throws Exception {
            asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("iPad"), "type", "FUN"));
            time.advanceSeconds(12 * 60 + 30);
            Long id = readBody(asChild(post("/api/sessions/stop"))).get("id").asLong();

            JsonNode corrected = readBody(asParent(put("/api/sessions/" + id),
                    Map.of("deviceId", deviceId("iMac"))).andExpect(status().isOk()));
            assertThat(corrected.get("seconds").asInt()).isEqualTo(12 * 60 + 30);
        }

        @Test
        void aParentCanMoveAnEntryToAnotherDay() throws Exception {
            Long id = readBody(asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 40, "deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();

            JsonNode moved = readBody(asParent(put("/api/sessions/" + id),
                    Map.of("date", MONDAY.toString())).andExpect(status().isOk()));
            assertThat(moved.get("day").asText()).isEqualTo(MONDAY.toString());
            assertThat(currentAsChild().get("balance").get("dayUsedSeconds").asInt())
                    .as("the minutes left Friday with the entry")
                    .isZero();
        }

        @Test
        void aRunningSessionCannotBeCorrectedUntilItIsStopped() throws Exception {
            Long id = readBody(asChild(post("/api/sessions/start"),
                    Map.of("deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();

            asParent(put("/api/sessions/" + id), Map.of("minutes", 5))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void aParentCanDeleteAnEntry() throws Exception {
            Long id = readBody(asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 40, "deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();

            asParent(delete("/api/sessions/" + id)).andExpect(status().isNoContent());
            assertThat(currentAsChild().get("balance").get("weekUsedSeconds").asInt()).isZero();
        }

        @Test
        void correctingAnEntryThatIsNotThereIsANotFound() throws Exception {
            asParent(put("/api/sessions/999999"), Map.of("minutes", 5))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("every change leaves a trail")
    class Audit {

        @Test
        void bookingWritesACreateRow() throws Exception {
            Long id = readBody(asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 30, "deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();

            assertThat(auditActions("SESSION", id)).containsExactly("CREATE");
        }

        @Test
        void startingAndStoppingWritesACreateAndAnUpdate() throws Exception {
            Long id = readBody(asChild(post("/api/sessions/start"),
                    Map.of("deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();
            time.advanceMinutes(10);
            asChild(post("/api/sessions/stop"));

            assertThat(auditActions("SESSION", id)).containsExactly("CREATE", "UPDATE");
        }

        @Test
        void aCorrectionRecordsWhatItChangedAndWhoChangedIt() throws Exception {
            Long id = readBody(asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 120, "deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();
            asParent(put("/api/sessions/" + id), Map.of("minutes", 30));

            Map<String, Object> row = jdbc.queryForMap("""
                    select old_value, new_value, user_id from audit_log
                    where entity = 'SESSION' and entity_id = ? and action = 'UPDATE'
                    """, id);
            assertThat((String) row.get("old_value")).contains("\"seconds\":7200");
            assertThat((String) row.get("new_value")).contains("\"seconds\":1800");
            assertThat(row.get("user_id")).isEqualTo(
                    jdbc.queryForObject("select id from users where username = ?", Long.class, PARENT_USERNAME));
        }

        @Test
        void aDeleteKeepsWhatWasThereBefore() throws Exception {
            Long id = readBody(asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 40, "deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();
            asParent(delete("/api/sessions/" + id));

            String old = jdbc.queryForObject("""
                    select old_value from audit_log
                    where entity = 'SESSION' and entity_id = ? and action = 'DELETE'
                    """, String.class, id);
            assertThat(old).contains("\"seconds\":2400");
        }

        @Test
        void anAdjustmentIsAudited() throws Exception {
            Long id = readBody(asParent(post("/api/adjustments"), Map.of(
                    "weekStart", MONDAY.toString(), "minutes", -60, "reason", "agreed on Sunday")))
                    .get("id").asLong();
            assertThat(auditActions("ADJUSTMENT", id)).containsExactly("CREATE");
        }

        private java.util.List<String> auditActions(String entity, Long id) {
            return jdbc.queryForList("""
                    select action from audit_log where entity = ? and entity_id = ? order by id
                    """, String.class, entity, id);
        }
    }

    private void holiday(String name, java.time.LocalDate from, java.time.LocalDate to) {
        jdbc.update("insert into holiday_periods (name, start_date, end_date) values (?, ?, ?)", name, from, to);
    }
}

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

            assertThat(stopped.get("minutes").asInt()).isEqualTo(25);
            assertThat(stopped.get("running").asBoolean()).isFalse();
            assertThat(stopped.get("autoClosed").asBoolean()).isFalse();
        }

        @Test
        void aRunningSessionAlreadyCountsAgainstTheBudget() throws Exception {
            asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("iPad"), "type", "FUN"));
            time.advanceMinutes(20);

            JsonNode current = currentAsChild();
            assertThat(current.get("openSession").get("elapsedMinutes").asInt()).isEqualTo(20);
            assertThat(current.get("balance").get("dayUsedMinutes").asInt())
                    .as("the countdown has to be honest while the timer runs")
                    .isEqualTo(20);
            assertThat(current.get("balance").get("remainingTodayMinutes").asInt()).isEqualTo(40);
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
            assertThat(booked.get("minutes").asInt()).isEqualTo(30);
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

            assertThat(currentAsChild().get("balance").get("weekUsedMinutes").asInt())
                    .as("a parent has no screen time of their own to account for")
                    .isEqualTo(45);
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
            assertThat(balance.get("weekUsedMinutes").asInt()).isZero();
            assertThat(balance.get("remainingWeekMinutes").asInt()).isEqualTo(480);
            assertThat(balance.get("quickUsedMinutes").asInt()).isEqualTo(10);
            assertThat(balance.get("remainingQuickMinutes").asInt()).isEqualTo(5);
        }

        @Test
        void theFilmCountsAgainstNothing() throws Exception {
            time.setLocal(FRIDAY, 20, 30);
            asParent(post("/api/sessions/manual"), Map.of(
                    "minutes", 110, "deviceId", deviceId("TV"), "type", "FILM"))
                    .andExpect(status().isCreated());

            JsonNode balance = currentAsChild().get("balance");
            assertThat(balance.get("weekUsedMinutes").asInt()).isZero();
            assertThat(balance.get("dayUsedMinutes").asInt()).isZero();
            assertThat(balance.get("quickUsedMinutes").asInt()).isZero();
        }

        @Test
        void theDashboardCarriesTheSevenDayStripWithTodayMarked() throws Exception {
            JsonNode current = currentAsChild();
            JsonNode week = current.get("week");
            assertThat(week).hasSize(7);
            assertThat(week.get(0).get("date").asText()).isEqualTo(MONDAY.toString());
            assertThat(week.get(6).get("date").asText()).isEqualTo(SUNDAY.toString());
            assertThat(week.get(4).get("today").asBoolean()).as("Friday is today").isTrue();
            assertThat(week.get(4).get("capMinutes").asInt()).isEqualTo(60);
            assertThat(week.get(5).get("capMinutes").asInt()).as("Saturday").isEqualTo(120);
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
            assertThat(balance.get("weeklyBudgetMinutes").asInt()).isEqualTo(480);
            assertThat(balance.get("adjustmentMinutes").asInt()).isEqualTo(-30);
            assertThat(balance.get("weekUsedMinutes").asInt()).isEqualTo(20);
            assertThat(balance.get("remainingWeekMinutes").asInt()).isEqualTo(430);
            assertThat(current.get("weekAdjustments").get(0).get("reason").asText())
                    .as("a number that drops must come with a reason the child can read")
                    .isEqualTo("bike left outside");
        }

        @Test
        void aHolidayWeekRaisesTheWeekdayCeiling() throws Exception {
            asParent(put("/api/weeks/" + MONDAY + "/holiday"), Map.of("holiday", true))
                    .andExpect(status().isOk());

            JsonNode current = currentAsChild();
            assertThat(current.get("holidayWeek").asBoolean()).isTrue();
            assertThat(current.get("balance").get("dailyCapMinutes").asInt())
                    .as("Friday of a holiday week gets the weekend ceiling")
                    .isEqualTo(120);
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
            assertThat(balance.get("weekUsedMinutes").asInt()).isEqualTo(455);
            assertThat(balance.get("remainingWeekMinutes").asInt()).isEqualTo(25);
            assertThat(balance.get("remainingTodayMinutes").asInt()).isEqualTo(60);
            assertThat(balance.get("availableNowMinutes").asInt()).isEqualTo(25);
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
            assertThat(corrected.get("minutes").asInt()).isEqualTo(30);
            assertThat(currentAsChild().get("balance").get("weekUsedMinutes").asInt()).isEqualTo(30);
        }

        @Test
        void aParentCanMoveAnEntryToAnotherDay() throws Exception {
            Long id = readBody(asChild(post("/api/sessions/manual"), Map.of(
                    "minutes", 40, "deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();

            JsonNode moved = readBody(asParent(put("/api/sessions/" + id),
                    Map.of("date", MONDAY.toString())).andExpect(status().isOk()));
            assertThat(moved.get("day").asText()).isEqualTo(MONDAY.toString());
            assertThat(currentAsChild().get("balance").get("dayUsedMinutes").asInt())
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
            assertThat(currentAsChild().get("balance").get("weekUsedMinutes").asInt()).isZero();
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
            assertThat((String) row.get("old_value")).contains("\"minutes\":120");
            assertThat((String) row.get("new_value")).contains("\"minutes\":30");
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
            assertThat(old).contains("\"minutes\":40");
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
}

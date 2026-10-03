package com.kiwih.screentime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.kiwih.screentime.service.AutoCloseScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("a forgotten stop costs the rest of the day, never the week")
class AutoCloseTest extends ApiTestBase {

    @Autowired
    AutoCloseScheduler scheduler;

    @Test
    void aSessionLeftRunningIsClosedAtOneMinuteToMidnightAndFlagged() throws Exception {
        time.setLocal(FRIDAY, 16, 0);
        Long id = readBody(asChild(post("/api/sessions/start"),
                Map.of("deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();

        time.setLocal(FRIDAY, 23, 59);
        assertThat(scheduler.closeAllOpen()).isEqualTo(1);

        Map<String, Object> row = jdbc.queryForMap(
                "select minutes, auto_closed, ended_at from sessions where id = ?", id);
        assertThat(row.get("auto_closed")).isEqualTo(true);
        assertThat(row.get("minutes"))
                .as("nearly eight hours elapsed, but the day only had 60 minutes left")
                .isEqualTo(60);
    }

    @Test
    void theCapIsWhatTheDayHadLeftAfterTheRestOfIt() throws Exception {
        asChild(post("/api/sessions/manual"), Map.of(
                "minutes", 45, "deviceId", deviceId("iPad"), "type", "FUN"))
                .andExpect(status().isCreated());

        time.setLocal(FRIDAY, 19, 0);
        Long id = readBody(asChild(post("/api/sessions/start"),
                Map.of("deviceId", deviceId("TV"), "type", "FUN"))).get("id").asLong();

        time.setLocal(FRIDAY, 23, 59);
        scheduler.closeAllOpen();

        assertThat(jdbc.queryForObject("select minutes from sessions where id = ?", Integer.class, id))
                .isEqualTo(15);
    }

    @Test
    void oneForgottenStopCannotWipeOutTheWeek() throws Exception {
        time.setLocal(FRIDAY, 16, 0);
        asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("iPad"), "type", "FUN"));

        time.setLocal(FRIDAY, 23, 59);
        scheduler.closeAllOpen();

        time.setLocal(SATURDAY, 9, 0);
        JsonNode balance = currentAsChild().get("balance");
        assertThat(balance.get("weekUsedMinutes").asInt()).isEqualTo(60);
        assertThat(balance.get("remainingWeekMinutes").asInt())
                .as("420 of 480 left, not nothing")
                .isEqualTo(420);
        assertThat(balance.get("remainingTodayMinutes").asInt())
                .as("Saturday starts fresh")
                .isEqualTo(120);
    }

    @Test
    void aSessionShorterThanWhatTheDayHadLeftIsChargedInFull() throws Exception {
        // a generous ceiling, so the elapsed time is the smaller of the two
        asParent(put("/api/settings"), Map.of("weekdayCapMinutes", "300"))
                .andExpect(status().isOk());

        time.setLocal(FRIDAY, 19, 59);
        Long id = readBody(asChild(post("/api/sessions/start"),
                Map.of("deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();

        time.setLocal(FRIDAY, 23, 59);
        scheduler.closeAllOpen();

        assertThat(jdbc.queryForObject("select minutes from sessions where id = ?", Integer.class, id))
                .as("240 minutes elapsed and the day had 300, so the cap does not bind")
                .isEqualTo(240);
    }

    @Test
    void aForgottenQuickSessionIsCappedAtTheQuickBudget() throws Exception {
        time.setLocal(FRIDAY, 9, 0);
        Long id = readBody(asChild(post("/api/sessions/start"),
                Map.of("deviceId", deviceId("iPad"), "type", "QUICK"))).get("id").asLong();

        time.setLocal(FRIDAY, 23, 59);
        scheduler.closeAllOpen();

        assertThat(jdbc.queryForObject("select minutes from sessions where id = ?", Integer.class, id))
                .isEqualTo(15);
    }

    @Test
    void aSessionLeftOpenFromAnEarlierDayIsClosedOnItsOwnDay() throws Exception {
        // the app was down overnight and the sweep runs a day late
        time.setLocal(MONDAY, 16, 0);
        Long id = readBody(asParent(post("/api/sessions/start"),
                Map.of("deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();

        time.setLocal(FRIDAY, 23, 59);
        scheduler.closeAllOpen();

        Map<String, Object> row = jdbc.queryForMap(
                "select minutes, ended_at from sessions where id = ?", id);
        assertThat(row.get("minutes")).isEqualTo(60);

        JsonNode week = readBody(asParent(get("/api/account/week").param("start", MONDAY.toString())));
        assertThat(week.get("days").get(0).get("usedMinutes").asInt())
                .as("the minutes stay on the Monday the session started on")
                .isEqualTo(60);
        assertThat(week.get("days").get(4).get("usedMinutes").asInt())
                .as("and never land on the Friday the sweep happened to run")
                .isZero();
    }

    @Test
    void theAutoCloseIsAudited() throws Exception {
        time.setLocal(FRIDAY, 16, 0);
        Long id = readBody(asChild(post("/api/sessions/start"),
                Map.of("deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();

        time.setLocal(FRIDAY, 23, 59);
        scheduler.closeAllOpen();

        String newValue = jdbc.queryForObject("""
                select new_value from audit_log
                where entity = 'SESSION' and entity_id = ? and action = 'UPDATE'
                """, String.class, id);
        assertThat(newValue).contains("\"autoClosed\":true").contains("\"minutes\":60");
    }

    @Test
    void theParentSeesTheFlagAndCanCorrectTheNumber() throws Exception {
        time.setLocal(FRIDAY, 16, 0);
        Long id = readBody(asChild(post("/api/sessions/start"),
                Map.of("deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();
        time.setLocal(FRIDAY, 23, 59);
        scheduler.closeAllOpen();

        JsonNode entries = readBody(asParent(get("/api/sessions")
                .param("from", FRIDAY.toString()).param("to", FRIDAY.toString())));
        assertThat(entries.get(0).get("autoClosed").asBoolean()).isTrue();

        asParent(put("/api/sessions/" + id), Map.of("minutes", 20)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select minutes from sessions where id = ?", Integer.class, id))
                .isEqualTo(20);
    }

    @Test
    void theSweepIsSafeToRunWhenNothingIsOpen() {
        assertThat(scheduler.closeAllOpen()).isZero();
    }

    @Test
    void aSessionStartedOnTheLongestNightIsStillCappedByTheDayNotTheHours() throws Exception {
        // the last Sunday in October is 25 hours long; the budget is unchanged
        LocalDate fallBack = LocalDate.of(2026, 10, 25);
        time.setLocal(fallBack, 1, 0);
        Long id = readBody(asParent(post("/api/sessions/start"),
                Map.of("deviceId", deviceId("iPad"), "type", "FUN"))).get("id").asLong();

        time.setLocal(fallBack, 23, 59);
        scheduler.closeAllOpen();

        assertThat(jdbc.queryForObject("select minutes from sessions where id = ?", Integer.class, id))
                .as("a Sunday ceiling is 120 minutes whether the day is 24 or 25 hours long")
                .isEqualTo(120);
    }
}

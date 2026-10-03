package com.kiwih.screentime.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("the weekly check, end to end")
class WeeklyCheckApiTest extends ApiTestBase {

    private static final LocalDate NEXT_WEEK = LocalDate.of(2026, 10, 5);

    @Test
    void aCleanWeekSetsTheBonusForTheFollowingWeek() throws Exception {
        book(200, MONDAY);
        book(100, FRIDAY);

        JsonNode response = check(305, false);

        assertThat(response.get("check").get("loggedMinutes").asInt()).isEqualTo(300);
        assertThat(response.get("check").get("reportedMinutes").asInt()).isEqualTo(305);
        assertThat(response.get("check").get("differenceMinutes").asInt()).isEqualTo(5);
        assertThat(response.get("check").get("clean").asBoolean()).isTrue();
        assertThat(response.get("check").get("penaltyMinutes").asInt()).isZero();
        assertThat(response.get("bonusSetForFollowingWeek").asBoolean()).isTrue();
        assertThat(response.get("followingWeek").asText()).isEqualTo(NEXT_WEEK.toString());
        assertThat(response.get("penalty").isNull()).isTrue();

        JsonNode flags = readBody(asParent(get("/api/weeks/" + NEXT_WEEK)));
        assertThat(flags.get("bonusActive").asBoolean()).isTrue();
    }

    @Test
    void theBonusRaisesNextWeeksBudgetAndWeekendCeiling() throws Exception {
        book(300, MONDAY);
        check(305, false);

        // move into the following week, on its Saturday
        time.setLocal(LocalDate.of(2026, 10, 10), 10, 0);
        JsonNode current = currentAsChild();

        assertThat(current.get("bonusActive").asBoolean()).isTrue();
        assertThat(current.get("balance").get("weeklyBudgetMinutes").asInt()).isEqualTo(540);
        assertThat(current.get("balance").get("dailyCapMinutes").asInt())
                .as("the extra hour needs a higher weekend ceiling to be usable")
                .isEqualTo(150);
    }

    @Test
    void timeMissingFromTheLogIsDeductedFromTheFollowingWeek() throws Exception {
        book(200, MONDAY);

        JsonNode response = check(260, false);

        assertThat(response.get("check").get("differenceMinutes").asInt()).isEqualTo(60);
        assertThat(response.get("check").get("penaltyMinutes").asInt()).isEqualTo(60);
        assertThat(response.get("check").get("clean").asBoolean()).isFalse();
        assertThat(response.get("bonusSetForFollowingWeek").asBoolean()).isFalse();

        JsonNode penalty = response.get("penalty");
        assertThat(penalty.get("weekStart").asText()).isEqualTo(NEXT_WEEK.toString());
        assertThat(penalty.get("minutes").asInt()).isEqualTo(-60);
        assertThat(penalty.get("reason").asText())
                .as("the child has to be able to read why")
                .contains("60 minutes more on the devices than in the log");
        assertThat(penalty.get("fromWeeklyCheck").asBoolean()).isTrue();
    }

    @Test
    void theDeductionShowsUpInNextWeeksBalance() throws Exception {
        book(200, MONDAY);
        check(300, false);

        time.setLocal(NEXT_WEEK, 10, 0);
        JsonNode balance = currentAsChild().get("balance");
        assertThat(balance.get("weeklyBudgetMinutes").asInt()).isEqualTo(480);
        assertThat(balance.get("adjustmentMinutes").asInt()).isEqualTo(-100);
        assertThat(balance.get("remainingWeekMinutes").asInt()).isEqualTo(380);
    }

    @Test
    void noWeekLosesMoreThanTheCap() throws Exception {
        book(60, MONDAY);
        JsonNode response = check(600, false);
        assertThat(response.get("check").get("differenceMinutes").asInt()).isEqualTo(540);
        assertThat(response.get("check").get("penaltyMinutes").asInt()).isEqualTo(120);
        assertThat(response.get("penalty").get("minutes").asInt()).isEqualTo(-120);
    }

    @Test
    void aDeliberateWeekCostsTheExtraHourOnTop() throws Exception {
        book(200, MONDAY);
        JsonNode response = check(230, true);
        assertThat(response.get("check").get("differenceMinutes").asInt()).isEqualTo(30);
        assertThat(response.get("check").get("penaltyMinutes").asInt()).isEqualTo(90);
        assertThat(response.get("check").get("deliberate").asBoolean()).isTrue();
        assertThat(response.get("penalty").get("reason").asText())
                .contains("rules worked around on purpose");
    }

    @Test
    void moreBookedThanTheDevicesSawCostsNothingButIsFlagged() throws Exception {
        book(200, MONDAY);
        JsonNode response = check(150, false);

        assertThat(response.get("check").get("differenceMinutes").asInt()).isEqualTo(-50);
        assertThat(response.get("check").get("penaltyMinutes").asInt()).isZero();
        assertThat(response.get("check").get("clean").asBoolean()).isFalse();
        assertThat(response.get("moreLoggedThanReported").asBoolean()).isTrue();
        assertThat(response.get("penalty").isNull()).isTrue();
        assertThat(response.get("bonusSetForFollowingWeek").asBoolean()).isFalse();
    }

    @Test
    void redoingACheckReplacesItAndDoesNotStackThePenalty() throws Exception {
        book(200, MONDAY);

        check(300, false);
        assertThat(adjustmentTotalFor(NEXT_WEEK)).isEqualTo(-100);
        assertThat(checkCount()).isEqualTo(1);

        // the parent realises a device reported wrongly and does the check again
        JsonNode second = check(240, false);

        assertThat(checkCount()).as("one check per week, the second replaces the first").isEqualTo(1);
        assertThat(adjustmentTotalFor(NEXT_WEEK))
                .as("the first penalty is reversed rather than added to")
                .isEqualTo(-40);
        assertThat(second.get("check").get("penaltyMinutes").asInt()).isEqualTo(40);
    }

    @Test
    void redoingACheckAsCleanRemovesThePenaltyAndGrantsTheBonus() throws Exception {
        book(200, MONDAY);
        check(300, false);
        assertThat(adjustmentTotalFor(NEXT_WEEK)).isEqualTo(-100);

        check(205, false);

        assertThat(adjustmentTotalFor(NEXT_WEEK)).isZero();
        assertThat(readBody(asParent(get("/api/weeks/" + NEXT_WEEK))).get("bonusActive").asBoolean())
                .isTrue();
    }

    @Test
    void redoingACheckAsNotCleanClearsAStaleBonus() throws Exception {
        book(200, MONDAY);
        check(205, false);
        assertThat(readBody(asParent(get("/api/weeks/" + NEXT_WEEK))).get("bonusActive").asBoolean())
                .isTrue();

        check(300, false);
        assertThat(readBody(asParent(get("/api/weeks/" + NEXT_WEEK))).get("bonusActive").asBoolean())
                .as("a bonus granted by a check that no longer exists must not survive")
                .isFalse();
    }

    @Test
    void theReversalIsAuditedAsVisiblyAsThePenaltyWas() throws Exception {
        book(200, MONDAY);
        check(300, false);
        check(240, false);

        List<String> adjustmentActions = jdbc.queryForList(
                "select action from audit_log where entity = 'ADJUSTMENT' order by id", String.class);
        assertThat(adjustmentActions).containsExactly("CREATE", "DELETE", "CREATE");

        List<String> checkActions = jdbc.queryForList(
                "select action from audit_log where entity = 'WEEKLY_CHECK' order by id", String.class);
        assertThat(checkActions).containsExactly("CREATE", "DELETE", "CREATE");
    }

    @Test
    void onlyFunMinutesAreCompared() throws Exception {
        book(100, MONDAY);
        bookType(30, MONDAY, "QUICK");
        bookType(120, MONDAY, "FILM");

        JsonNode response = check(100, false);
        assertThat(response.get("check").get("loggedMinutes").asInt())
                .as("quick time and the film are outside the weekly budget, so outside the check")
                .isEqualTo(100);
        assertThat(response.get("check").get("clean").asBoolean()).isTrue();
    }

    @Test
    void thePerDeviceBreakdownIsKeptAndCanBeReadBack() throws Exception {
        book(100, MONDAY);
        asParent(post("/api/checks"), Map.of(
                "weekStart", MONDAY.toString(),
                "reported", List.of(
                        Map.of("deviceId", deviceId("iPad"), "minutes", 70),
                        Map.of("deviceId", deviceId("TV"), "minutes", 35)),
                "deliberate", false)).andExpect(status().isOk());

        JsonNode week = readBody(asParent(get("/api/account/week").param("start", MONDAY.toString())));
        JsonNode reported = week.get("check").get("reported");
        assertThat(reported).hasSize(2);
        assertThat(reported.get(0).get("deviceName").asText()).isNotBlank();
        assertThat(week.get("check").get("reportedMinutes").asInt()).isEqualTo(105);
    }

    @Test
    void aDeviceListedTwiceIsRefused() throws Exception {
        asParent(post("/api/checks"), Map.of(
                "weekStart", MONDAY.toString(),
                "reported", List.of(
                        Map.of("deviceId", deviceId("iPad"), "minutes", 30),
                        Map.of("deviceId", deviceId("iPad"), "minutes", 40)),
                "deliberate", false)).andExpect(status().isBadRequest());
    }

    @Test
    void anUnknownDeviceIsRefused() throws Exception {
        asParent(post("/api/checks"), Map.of(
                "weekStart", MONDAY.toString(),
                "reported", List.of(Map.of("deviceId", 999999, "minutes", 30)),
                "deliberate", false)).andExpect(status().isNotFound());
    }

    @Test
    void anyDayOfAWeekIdentifiesTheWeek() throws Exception {
        book(200, MONDAY);
        // the parent sends the Sunday rather than the Monday
        JsonNode response = check(205, false, SUNDAY);
        assertThat(response.get("check").get("weekStart").asText()).isEqualTo(MONDAY.toString());
    }

    @Test
    void aCheckAppearsInTheHistory() throws Exception {
        book(200, MONDAY);
        check(300, false);

        JsonNode history = readBody(asChild(get("/api/account/history").param("weeks", "2")));
        assertThat(history).hasSize(2);
        assertThat(history.get(0).get("weekStart").asText()).isEqualTo(MONDAY.toString());
        assertThat(history.get(0).get("check").get("penaltyMinutes").asInt()).isEqualTo(100);
        assertThat(history.get(0).get("usedMinutes").asInt()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ helpers

    private void book(int minutes, LocalDate day) throws Exception {
        bookType(minutes, day, "FUN");
    }

    private void bookType(int minutes, LocalDate day, String type) throws Exception {
        asParent(post("/api/sessions/manual"), Map.of(
                "minutes", minutes, "deviceId", deviceId("iPad"), "type", type,
                "date", day.toString())).andExpect(status().isCreated());
    }

    private JsonNode check(int reportedMinutes, boolean deliberate) throws Exception {
        return check(reportedMinutes, deliberate, MONDAY);
    }

    private JsonNode check(int reportedMinutes, boolean deliberate, LocalDate weekStart) throws Exception {
        return readBody(asParent(post("/api/checks"), Map.of(
                "weekStart", weekStart.toString(),
                "reported", List.of(Map.of("deviceId", deviceId("iPad"), "minutes", reportedMinutes)),
                "deliberate", deliberate)).andExpect(status().isOk()));
    }

    private int adjustmentTotalFor(LocalDate weekStart) {
        Integer total = jdbc.queryForObject(
                "select coalesce(sum(minutes), 0) from adjustments where week_start = ?",
                Integer.class, weekStart);
        return total == null ? 0 : total;
    }

    private int checkCount() {
        return jdbc.queryForObject("select count(*) from weekly_checks", Integer.class);
    }
}

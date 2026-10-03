package com.kiwih.screentime.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("settings are versioned rows a parent changes per scope, and they have to hold together")
class SettingsApiTest extends ApiTestBase {

    private static final LocalDate NEXT_MONDAY = MONDAY.plusWeeks(1);

    @Nested
    @DisplayName("the overview")
    class Overview {

        @Test
        void bothValueSetsAndTheThresholdHaveTheDocumentedDefaults() throws Exception {
            JsonNode settings = readBody(asParent(get("/api/settings")).andExpect(status().isOk()));

            assertThat(settings.get("thisWeek").asText()).isEqualTo(MONDAY.toString());
            assertThat(settings.get("nextWeek").asText()).isEqualTo(NEXT_MONDAY.toString());
            JsonNode term = settings.get("term").get("thisWeek");
            assertThat(term.get("weeklyMinutes").asText()).isEqualTo("480");
            assertThat(term.get("weekdayCapMinutes").asText()).isEqualTo("60");
            assertThat(term.get("cutoffHour").asText()).isEqualTo("20");
            JsonNode holiday = settings.get("holiday").get("thisWeek");
            assertThat(holiday.get("weeklyMinutes").asText()).isEqualTo("720");
            assertThat(holiday.get("weekdayCapMinutes").asText()).isEqualTo("120");
            assertThat(holiday.get("cutoffHour").asText()).isEqualTo("21");
            assertThat(settings.get("general").get("thisWeek").get("holidayWeekThresholdDays").asText())
                    .isEqualTo("4");
            assertThat(settings.get("changes")).isEmpty();
        }

        @Test
        void theHistoryKeepsTheOldValue() throws Exception {
            change("TERM", Map.of("weeklyMinutes", "400"), null).andExpect(status().isOk());

            JsonNode history = readBody(asParent(get("/api/settings"))).get("term").get("history");
            List<String> weekly = history.findParents("key").stream()
                    .filter(r -> r.get("key").asText().equals("weeklyMinutes"))
                    .map(r -> r.get("value").asText() + "@" + r.get("validFrom").asText())
                    .toList();
            assertThat(weekly).containsExactly("480@2000-01-03", "400@" + MONDAY);
        }

        @Test
        void aChildCannotReadTheOverview() throws Exception {
            asChild(get("/api/settings")).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("a change applies from this Monday or next Monday")
    class Changing {

        @Test
        void withoutADateItAppliesFromThisMondayAndTakesEffectAtOnce() throws Exception {
            change("TERM", Map.of("weekdayCapMinutes", "90"), null).andExpect(status().isOk());

            assertThat(currentAsChild().get("balance").get("dailyCapSeconds").asInt())
                    .as("no restart, no deployment: the rules are built from the rows on every request")
                    .isEqualTo(90 * 60);
        }

        @Test
        void aCutOffChangeMovesTheEveningImmediately() throws Exception {
            time.setLocal(FRIDAY, 20, 30);
            asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("iPad"), "type", "FUN"))
                    .andExpect(status().isForbidden());

            change("TERM", Map.of("cutoffHour", "21"), MONDAY).andExpect(status().isOk());

            asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("iPad"), "type", "FUN"))
                    .andExpect(status().isCreated());
        }

        @Test
        void fromNextMondayLeavesThisWeekAlone() throws Exception {
            JsonNode settings = readBody(change("TERM", Map.of("weeklyMinutes", "420"), NEXT_MONDAY)
                    .andExpect(status().isOk()));

            assertThat(settings.get("term").get("thisWeek").get("weeklyMinutes").asText()).isEqualTo("480");
            assertThat(settings.get("term").get("nextWeek").get("weeklyMinutes").asText()).isEqualTo("420");
            assertThat(currentAsChild().get("balance").get("weeklyBudgetSeconds").asInt()).isEqualTo(480 * 60);
            assertThat(effective(NEXT_MONDAY).get("values").get("weeklyMinutes").asInt()).isEqualTo(420);
        }

        @Test
        void aChangeTodayLeavesLastWeekAlone() throws Exception {
            change("TERM", Map.of("weeklyMinutes", "400"), null).andExpect(status().isOk());

            assertThat(currentAsChild().get("balance").get("weeklyBudgetSeconds").asInt()).isEqualTo(400 * 60);
            JsonNode lastWeek = readBody(asParent(get("/api/account/week")
                    .param("start", MONDAY.minusWeeks(1).toString())));
            assertThat(lastWeek.get("balance").get("weeklyBudgetSeconds").asInt())
                    .as("last week's budget is what it was")
                    .isEqualTo(480 * 60);
        }

        @Test
        void anyOtherDateIsRefused() throws Exception {
            for (LocalDate date : List.of(MONDAY.minusWeeks(1), MONDAY.plusDays(2), NEXT_MONDAY.plusWeeks(1))) {
                JsonNode error = readBody(change("TERM", Map.of("weeklyMinutes", "420"), date)
                        .andExpect(status().isBadRequest()));
                assertThat(error.get("message").asText()).contains(MONDAY.toString()).contains(NEXT_MONDAY.toString());
            }
        }

        @Test
        void holidayValuesChangeOnTheirOwn() throws Exception {
            JsonNode settings = readBody(change("HOLIDAY", Map.of("cutoffHour", "22"), null)
                    .andExpect(status().isOk()));
            assertThat(settings.get("holiday").get("thisWeek").get("cutoffHour").asText()).isEqualTo("22");
            assertThat(settings.get("term").get("thisWeek").get("cutoffHour").asText()).isEqualTo("20");
        }

        @Test
        void theThresholdIsAGeneralSetting() throws Exception {
            change("GLOBAL", Map.of("holidayWeekThresholdDays", "3"), null).andExpect(status().isOk());
            holiday("Autumn holidays", FRIDAY, FRIDAY.plusDays(16));

            assertThat(effective(MONDAY).get("scope").asText())
                    .as("three holiday days now make a holiday week")
                    .isEqualTo("HOLIDAY");
        }

        @Test
        void aChangeIsANewRowAndTheOldValueIsKept() throws Exception {
            change("TERM", Map.of("weeklyMinutes", "400"), null).andExpect(status().isOk());

            var rows = jdbc.queryForList("""
                    select value, valid_from::text as valid_from from settings
                    where scope = 'TERM' and key = 'weeklyMinutes' order by id
                    """);
            assertThat(rows).extracting(r -> r.get("value")).containsExactly("480", "400");
            assertThat(rows.get(1).get("valid_from")).isEqualTo(MONDAY.toString());
        }

        @Test
        void aChildCannotChangeAnything() throws Exception {
            asChild(put("/api/settings"), Map.of("scope", "TERM", "values", Map.of("weeklyMinutes", "9999")))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("a change has to hold together")
    class Validation {

        @Test
        void ceilingsThatDoNotExceedTheWeeklyBudgetAreRefusedAndBothNumbersAreNamed() throws Exception {
            JsonNode error = readBody(change("TERM", Map.of("weekendCapMinutes", "30"), null)
                    .andExpect(status().isBadRequest()));
            assertThat(error.get("message").asText()).contains("360").contains("480");
        }

        @Test
        void raisingTheWeeklyBudgetPastTheCeilingsIsRefused() throws Exception {
            JsonNode error = readBody(change("TERM", Map.of("weeklyMinutes", "600"), null)
                    .andExpect(status().isBadRequest()));
            assertThat(error.get("message").asText()).contains("540").contains("600");
        }

        @Test
        void everyBrokenRuleComesBackWithTheFieldsItConcerns() throws Exception {
            JsonNode error = readBody(change("TERM", Map.of("weekendCapMinutes", "30", "cutoffHour", "8"), null)
                    .andExpect(status().isBadRequest()));

            JsonNode problems = error.get("details").get("problems");
            assertThat(problems.findValuesAsText("rule"))
                    .containsExactlyInAnyOrder("DAILY_CEILINGS_EXCEED_WEEK", "CUTOFF_HOUR_RANGE");
            assertThat(problems.get(0).get("keys").toString()).contains("weekendCapMinutes");
        }

        @Test
        void aRefusedChangeLeavesEveryRowAsItWas() throws Exception {
            change("TERM", Map.of("weekdayCapMinutes", "90", "weekendCapMinutes", "10"), null)
                    .andExpect(status().isBadRequest());

            JsonNode term = readBody(asParent(get("/api/settings"))).get("term").get("thisWeek");
            assertThat(term.get("weekdayCapMinutes").asText())
                    .as("the whole set is validated before anything is written")
                    .isEqualTo("60");
            assertThat(jdbc.queryForObject("select count(*) from settings where created_by is not null",
                    Integer.class)).isZero();
        }

        @Test
        void aChangeThisWeekIsCheckedAgainstWhatIsPlannedForNextWeek() throws Exception {
            change("TERM", Map.of("weeklyMinutes", "520"), NEXT_MONDAY).andExpect(status().isOk());

            JsonNode error = readBody(change("TERM", Map.of("weekdayCapMinutes", "55"), MONDAY)
                    .andExpect(status().isBadRequest()));
            assertThat(error.get("message").asText()).contains(NEXT_MONDAY.toString()).contains("520");
        }

        @Test
        void aCutOffInTheMorningIsRefused() throws Exception {
            change("TERM", Map.of("cutoffHour", "11"), null).andExpect(status().isBadRequest());
            change("HOLIDAY", Map.of("cutoffHour", "25"), null).andExpect(status().isBadRequest());
        }

        @Test
        void aValueThatIsNotANumberIsRefused() throws Exception {
            change("TERM", Map.of("weeklyMinutes", "lots"), null).andExpect(status().isBadRequest());
            change("GLOBAL", Map.of("holidayWeekThresholdDays", "four"), null).andExpect(status().isBadRequest());
        }

        @Test
        void aThresholdOutsideTheWeekIsRefused() throws Exception {
            change("GLOBAL", Map.of("holidayWeekThresholdDays", "0"), null).andExpect(status().isBadRequest());
            change("GLOBAL", Map.of("holidayWeekThresholdDays", "8"), null).andExpect(status().isBadRequest());
        }

        @Test
        void anUnknownSettingIsRefused() throws Exception {
            JsonNode error = readBody(change("TERM", Map.of("unlimitedScreens", "true"), null)
                    .andExpect(status().isBadRequest()));
            assertThat(error.get("message").asText()).contains("unlimitedScreens");
        }

        @Test
        void aRequestWithoutAScopeIsRefused() throws Exception {
            asParent(put("/api/settings"), Map.of("values", Map.of("weeklyMinutes", "420")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("the change log")
    class ChangeLog {

        @Test
        void everyChangeIsAuditedWithTheOldAndTheNewValue() throws Exception {
            change("TERM", Map.of("weeklyMinutes", "400"), null).andExpect(status().isOk());

            var rows = jdbc.queryForList("select old_value, new_value from audit_log where entity = 'SETTING'");
            assertThat(rows).hasSize(1);
            assertThat((String) rows.get(0).get("old_value")).contains("480");
            assertThat((String) rows.get(0).get("new_value")).contains("400");
        }

        @Test
        void aChangeRecordsWhoMadeIt() throws Exception {
            change("TERM", Map.of("toleranceMinutes", "15"), null).andExpect(status().isOk());

            Long createdBy = jdbc.queryForObject(
                    "select created_by from settings where key = 'toleranceMinutes' and created_by is not null",
                    Long.class);
            assertThat(createdBy).isEqualTo(
                    jdbc.queryForObject("select id from users where username = ?", Long.class, PARENT_USERNAME));
        }

        @Test
        void saysWhoChangedWhatWhenAndFromWhichValueToWhich() throws Exception {
            change("TERM", Map.of("weeklyMinutes", "420"), NEXT_MONDAY).andExpect(status().isOk());

            JsonNode entry = readBody(asParent(get("/api/settings"))).get("changes").get(0);
            assertThat(entry.get("kind").asText()).isEqualTo("SETTING");
            assertThat(entry.get("scope").asText()).isEqualTo("TERM");
            assertThat(entry.get("key").asText()).isEqualTo("weeklyMinutes");
            assertThat(entry.get("from").asText()).isEqualTo("480");
            assertThat(entry.get("to").asText()).isEqualTo("420");
            assertThat(entry.get("validFrom").asText()).isEqualTo(NEXT_MONDAY.toString());
            assertThat(entry.get("by").asText()).isNotBlank();
            assertThat(entry.get("at").asText()).isNotBlank();
        }

        @Test
        void holidayPeriodsAreInItToo() throws Exception {
            Long id = readBody(asParent(post("/api/holidays"), Map.of(
                    "name", "Autumn holidays", "startDate", "2026-10-10", "endDate", "2026-10-25")))
                    .get("id").asLong();
            time.advanceMinutes(1);
            asParent(put("/api/holidays/" + id), Map.of(
                    "name", "Autumn holidays", "startDate", "2026-10-10", "endDate", "2026-10-26"))
                    .andExpect(status().isOk());

            JsonNode changes = readBody(asParent(get("/api/settings"))).get("changes");
            assertThat(changes).hasSize(2);
            JsonNode update = changes.get(0);
            assertThat(update.get("kind").asText()).isEqualTo("HOLIDAY_PERIOD");
            assertThat(update.get("action").asText()).isEqualTo("UPDATE");
            assertThat(update.get("from").asText()).isEqualTo("Autumn holidays, 2026-10-10 to 2026-10-25");
            assertThat(update.get("to").asText()).isEqualTo("Autumn holidays, 2026-10-10 to 2026-10-26");
            assertThat(changes.get(1).get("action").asText()).isEqualTo("CREATE");
        }
    }

    @Nested
    @DisplayName("the values in force for a week")
    class Effective {

        @Test
        void everybodyCanReadThem() throws Exception {
            JsonNode effective = readBody(asChild(get("/api/settings/effective")).andExpect(status().isOk()));
            assertThat(effective.get("weekStart").asText()).isEqualTo(MONDAY.toString());
            assertThat(effective.get("scope").asText()).isEqualTo("TERM");
            assertThat(effective.get("values").get("weeklyMinutes").asInt()).isEqualTo(480);
            assertThat(effective.get("weeklyBudgetMinutes").asInt()).isEqualTo(480);
            assertThat(effective.get("holidayDayCount").asInt()).isZero();
            assertThat(effective.get("holidayWeekThresholdDays").asInt()).isEqualTo(4);
            assertThat(effective.has("holidayPeriodName") && !effective.get("holidayPeriodName").isNull())
                    .isFalse();
            assertThat(effective.get("days")).hasSize(7);
        }

        @Test
        void aWeekMostlyInTheHolidaysSaysWhy() throws Exception {
            // Thursday 1 to Sunday 4 October: four of seven
            holiday("Autumn holidays", MONDAY.plusDays(3), SUNDAY.plusDays(14));

            JsonNode effective = effective(MONDAY);
            assertThat(effective.get("scope").asText()).isEqualTo("HOLIDAY");
            assertThat(effective.get("holidayDayCount").asInt()).isEqualTo(4);
            assertThat(effective.get("holidayPeriodName").asText()).isEqualTo("Autumn holidays");
            assertThat(effective.get("values").get("weeklyMinutes").asInt()).isEqualTo(720);
            assertThat(effective.get("values").get("cutoffHour").asInt()).isEqualTo(21);
            assertThat(effective.get("days").get(0).get("holiday").asBoolean()).isFalse();
            assertThat(effective.get("days").get(3).get("holiday").asBoolean()).isTrue();
            assertThat(effective.get("days").get(0).get("capMinutes").asInt()).isEqualTo(120);
        }

        @Test
        void aTermWeekWithHolidayDaysShowsTheirCeiling() throws Exception {
            holiday("Autumn holidays", SATURDAY, SATURDAY.plusDays(15));
            change("HOLIDAY", Map.of("weekendCapMinutes", "140", "bonusWeekendCapMinutes", "160"), null)
                    .andExpect(status().isOk());

            JsonNode effective = effective(MONDAY);
            assertThat(effective.get("scope").asText()).isEqualTo("TERM");
            assertThat(effective.get("holidayDayCount").asInt()).isEqualTo(2);
            assertThat(effective.get("days").get(4).get("capMinutes").asInt()).as("Friday").isEqualTo(60);
            assertThat(effective.get("days").get(5).get("capMinutes").asInt()).as("Saturday").isEqualTo(140);
        }

        @Test
        void anyWeekCanBeAskedFor() throws Exception {
            assertThat(effective(MONDAY.plusDays(10)).get("weekStart").asText()).isEqualTo(NEXT_MONDAY.toString());
        }

        @Test
        void theDashboardCarriesThemSoTheChildSeesHolidayRules() throws Exception {
            holiday("Autumn holidays", MONDAY.minusDays(2), SUNDAY.plusDays(7));

            JsonNode current = currentAsChild();
            assertThat(current.get("rules").get("scope").asText()).isEqualTo("HOLIDAY");
            assertThat(current.get("rules").get("holidayPeriodName").asText()).isEqualTo("Autumn holidays");
            assertThat(current.get("rules").get("values").get("weeklyMinutes").asInt()).isEqualTo(720);
        }

        @Test
        void theWeeklyBudgetIncludesAnActiveBonus() throws Exception {
            jdbc.update("insert into week_flags (week_start, bonus_active) values (?, true)", MONDAY);
            JsonNode effective = effective(MONDAY);
            assertThat(effective.get("bonusActive").asBoolean()).isTrue();
            assertThat(effective.get("weeklyBudgetMinutes").asInt()).isEqualTo(480 + 60);
            assertThat(effective.get("days").get(5).get("capMinutes").asInt()).isEqualTo(150);
        }
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions change(String scope, Map<String, String> values, LocalDate validFrom) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("scope", scope);
        body.put("values", values);
        if (validFrom != null) {
            body.put("validFrom", validFrom.toString());
        }
        return asParent(put("/api/settings"), body);
    }

    private JsonNode effective(LocalDate week) throws Exception {
        return readBody(asParent(get("/api/settings/effective").param("week", week.toString()))
                .andExpect(status().isOk()));
    }

    private void holiday(String name, LocalDate from, LocalDate to) {
        jdbc.update("insert into holiday_periods (name, start_date, end_date) values (?, ?, ?)", name, from, to);
    }
}

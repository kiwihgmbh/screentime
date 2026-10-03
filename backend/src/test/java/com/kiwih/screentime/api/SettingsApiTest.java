package com.kiwih.screentime.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("settings are rows a parent can change, and they have to hold together")
class SettingsApiTest extends ApiTestBase {

    @Test
    void theDefaultsAreWhatTheSpecificationSays() throws Exception {
        JsonNode settings = readBody(asParent(get("/api/settings")));
        assertThat(settings.get("weeklyMinutes").asText()).isEqualTo("480");
        assertThat(settings.get("weekdayCapMinutes").asText()).isEqualTo("60");
        assertThat(settings.get("weekendCapMinutes").asText()).isEqualTo("120");
        assertThat(settings.get("cutoffHour").asText()).isEqualTo("20");
    }

    @Test
    void aChangeTakesEffectOnTheNextRead() throws Exception {
        asParent(put("/api/settings"), Map.of("weekdayCapMinutes", "90"))
                .andExpect(status().isOk());

        assertThat(currentAsChild().get("balance").get("dailyCapMinutes").asInt())
                .as("no restart, no deployment: the rules are built from the rows on every request")
                .isEqualTo(90);
    }

    @Test
    void aCutOffChangeMovesTheEveningImmediately() throws Exception {
        time.setLocal(FRIDAY, 20, 30);
        asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("iPad"), "type", "FUN"))
                .andExpect(status().isForbidden());

        asParent(put("/api/settings"), Map.of("cutoffHour", "21")).andExpect(status().isOk());

        asChild(post("/api/sessions/start"), Map.of("deviceId", deviceId("iPad"), "type", "FUN"))
                .andExpect(status().isCreated());
    }

    @Test
    void ceilingsThatDoNotExceedTheWeeklyBudgetAreRefusedAndBothNumbersAreNamed() throws Exception {
        JsonNode error = readBody(asParent(put("/api/settings"), Map.of("weekendCapMinutes", "30"))
                .andExpect(status().isBadRequest()));

        assertThat(error.get("message").asText())
                .contains("360")
                .contains("480");
    }

    @Test
    void aRefusedChangeLeavesEveryRowAsItWas() throws Exception {
        asParent(put("/api/settings"), Map.of(
                "weekdayCapMinutes", "90", "weekendCapMinutes", "10"))
                .andExpect(status().isBadRequest());

        JsonNode settings = readBody(asParent(get("/api/settings")));
        assertThat(settings.get("weekdayCapMinutes").asText())
                .as("the whole set is validated before anything is written")
                .isEqualTo("60");
        assertThat(settings.get("weekendCapMinutes").asText()).isEqualTo("120");
    }

    @Test
    void anUnknownSettingIsRefused() throws Exception {
        JsonNode error = readBody(asParent(put("/api/settings"), Map.of("unlimitedScreens", "true"))
                .andExpect(status().isBadRequest()));
        assertThat(error.get("message").asText()).contains("unlimitedScreens");
    }

    @Test
    void raisingTheWeeklyBudgetPastTheCeilingsIsRefused() throws Exception {
        // the ceilings add up to 540, so a weekly budget of 600 would never bind
        JsonNode error = readBody(asParent(put("/api/settings"), Map.of("weeklyMinutes", "600"))
                .andExpect(status().isBadRequest()));
        assertThat(error.get("message").asText()).contains("540").contains("600");
    }

    @Test
    void anImpossibleCutOffHourIsRefused() throws Exception {
        asParent(put("/api/settings"), Map.of("cutoffHour", "25")).andExpect(status().isBadRequest());
    }

    @Test
    void aValueThatIsNotANumberIsRefused() throws Exception {
        asParent(put("/api/settings"), Map.of("weeklyMinutes", "lots")).andExpect(status().isBadRequest());
    }

    @Test
    void everyChangeIsAudited() throws Exception {
        // 600 would be refused: the ceilings only add up to 540
        asParent(put("/api/settings"), Map.of("weeklyMinutes", "400")).andExpect(status().isOk());

        var rows = jdbc.queryForList("""
                select old_value, new_value from audit_log where entity = 'SETTING'
                """);
        assertThat(rows).hasSize(1);
        assertThat((String) rows.get(0).get("old_value")).contains("480");
        assertThat((String) rows.get(0).get("new_value")).contains("400");
    }

    @Test
    void aChangeRecordsWhoMadeIt() throws Exception {
        asParent(put("/api/settings"), Map.of("toleranceMinutes", "15")).andExpect(status().isOk());

        Long updatedBy = jdbc.queryForObject(
                "select updated_by from settings where key = 'toleranceMinutes'", Long.class);
        assertThat(updatedBy).isEqualTo(
                jdbc.queryForObject("select id from users where username = ?", Long.class, PARENT_USERNAME));
    }
}

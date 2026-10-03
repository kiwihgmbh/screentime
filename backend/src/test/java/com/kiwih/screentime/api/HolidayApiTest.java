package com.kiwih.screentime.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("holiday periods are the parents' to enter, and they never overlap")
class HolidayApiTest extends ApiTestBase {

    @Test
    void aParentAddsAPeriodAndSeesItInTheList() throws Exception {
        JsonNode created = readBody(create("Autumn holidays", "2026-10-10", "2026-10-25")
                .andExpect(status().isCreated()));
        assertThat(created.get("id").asLong()).isPositive();
        assertThat(created.get("name").asText()).isEqualTo("Autumn holidays");
        assertThat(created.get("days").asInt()).as("both ends count").isEqualTo(16);
        assertThat(created.get("createdBy").asText()).isNotBlank();

        JsonNode list = readBody(asParent(get("/api/holidays")).andExpect(status().isOk()));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("startDate").asText()).isEqualTo("2026-10-10");
        assertThat(list.get(0).get("endDate").asText()).isEqualTo("2026-10-25");
    }

    @Test
    void theListCanBeLimitedToARange() throws Exception {
        create("Autumn holidays", "2026-10-10", "2026-10-25");
        create("Christmas", "2026-12-19", "2027-01-03");

        JsonNode december = readBody(asParent(get("/api/holidays")
                .param("from", "2026-12-01").param("to", "2026-12-31")));
        assertThat(december.findValuesAsText("name")).containsExactly("Christmas");

        JsonNode fromNovember = readBody(asParent(get("/api/holidays").param("from", "2026-10-25")));
        assertThat(fromNovember.findValuesAsText("name"))
                .as("a period that ends on the first day of the range is in it")
                .containsExactly("Autumn holidays", "Christmas");
    }

    @Test
    void anOverlapIsAConflictThatNamesThePeriodInTheWay() throws Exception {
        create("Autumn holidays", "2026-10-10", "2026-10-25");

        JsonNode error = readBody(create("Autumn camp", "2026-10-25", "2026-10-28")
                .andExpect(status().isConflict()));
        assertThat(error.get("message").asText()).contains("Autumn holidays")
                .contains("2026-10-10").contains("2026-10-25");
        assertThat(error.get("details").get("conflictingPeriod").get("name").asText()).isEqualTo("Autumn holidays");
        assertThat(readBody(asParent(get("/api/holidays")))).hasSize(1);
    }

    @Test
    void periodsThatOnlyTouchAreFine() throws Exception {
        create("Autumn holidays", "2026-10-10", "2026-10-25");
        create("After", "2026-10-26", "2026-10-28").andExpect(status().isCreated());
    }

    @Test
    void aPeriodCanBeChangedWithoutConflictingWithItself() throws Exception {
        Long id = readBody(create("Autumn holidays", "2026-10-10", "2026-10-25")).get("id").asLong();

        JsonNode changed = readBody(update(id, "Autumn holidays", "2026-10-09", "2026-10-25")
                .andExpect(status().isOk()));
        assertThat(changed.get("startDate").asText()).isEqualTo("2026-10-09");
    }

    @Test
    void aChangeIntoAnotherPeriodIsAConflict() throws Exception {
        create("Autumn holidays", "2026-10-10", "2026-10-25");
        Long christmas = readBody(create("Christmas", "2026-12-19", "2027-01-03")).get("id").asLong();

        update(christmas, "Christmas", "2026-10-20", "2027-01-03").andExpect(status().isConflict());
    }

    @Test
    void aPeriodCanBeDeleted() throws Exception {
        Long id = readBody(create("Autumn holidays", "2026-10-10", "2026-10-25")).get("id").asLong();
        asParent(delete("/api/holidays/" + id)).andExpect(status().isNoContent());
        assertThat(readBody(asParent(get("/api/holidays")))).isEmpty();
    }

    @Test
    void aMissingPeriodIsNotFound() throws Exception {
        update(999_999L, "Nothing", "2026-10-10", "2026-10-25").andExpect(status().isNotFound());
        asParent(delete("/api/holidays/999999")).andExpect(status().isNotFound());
    }

    @Test
    void aBackwardsOrNamelessPeriodIsRefused() throws Exception {
        create("Backwards", "2026-10-25", "2026-10-10").andExpect(status().isBadRequest());
        create(" ", "2026-10-10", "2026-10-25").andExpect(status().isBadRequest());
        asParent(post("/api/holidays"), Map.of("name", "No end", "startDate", "2026-10-10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void everyChangeIsAudited() throws Exception {
        Long id = readBody(create("Autumn holidays", "2026-10-10", "2026-10-25")).get("id").asLong();
        update(id, "Autumn holidays", "2026-10-10", "2026-10-26");
        asParent(delete("/api/holidays/" + id));

        var actions = jdbc.queryForList(
                "select action from audit_log where entity = 'HOLIDAY_PERIOD' and entity_id = ? order by id",
                String.class, id);
        assertThat(actions).containsExactly("CREATE", "UPDATE", "DELETE");
        String deleted = jdbc.queryForObject("select old_value from audit_log where entity = 'HOLIDAY_PERIOD' "
                + "and action = 'DELETE'", String.class);
        assertThat(deleted).contains("2026-10-26");
    }

    @Test
    void aPeriodCoveringTheWeekMakesItAHolidayWeekAtOnce() throws Exception {
        // Thursday 1 to Sunday 4 October: four of seven
        create("Autumn holidays", THURSDAY.toString(), "2026-10-18");

        JsonNode current = currentAsChild();
        assertThat(current.get("holidayWeek").asBoolean()).isTrue();
        assertThat(current.get("balance").get("weeklyBudgetSeconds").asInt()).isEqualTo(720 * 60);
    }

    @Test
    void aChildCannotTouchHolidays() throws Exception {
        Long id = readBody(create("Autumn holidays", "2026-10-10", "2026-10-25")).get("id").asLong();

        asChild(get("/api/holidays")).andExpect(status().isForbidden());
        asChild(post("/api/holidays"), Map.of("name", "Every day", "startDate", "2026-10-01",
                "endDate", "2026-12-31")).andExpect(status().isForbidden());
        asChild(put("/api/holidays/" + id), Map.of("name", "Longer", "startDate", "2026-10-01",
                "endDate", "2026-12-31")).andExpect(status().isForbidden());
        asChild(delete("/api/holidays/" + id)).andExpect(status().isForbidden());
    }

    private static final java.time.LocalDate THURSDAY = MONDAY.plusDays(3);

    private ResultActions create(String name, String from, String to) throws Exception {
        return asParent(post("/api/holidays"), Map.of("name", name, "startDate", from, "endDate", to));
    }

    private ResultActions update(Long id, String name, String from, String to) throws Exception {
        return asParent(put("/api/holidays/" + id), Map.of("name", name, "startDate", from, "endDate", to));
    }
}

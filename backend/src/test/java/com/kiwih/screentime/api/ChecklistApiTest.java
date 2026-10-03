package com.kiwih.screentime.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The clock stands on Friday 2 October 2026, 16:00, unless a test moves it. */
@DisplayName("the checklist: parents set it, the child ticks it before every start of screen time")
class ChecklistApiTest extends ApiTestBase {

    @Nested
    @DisplayName("parents manage the items")
    class Managing {

        @Test
        void anItemIsAddedAndListed() throws Exception {
            JsonNode created = readBody(add("Homework first", List.of(), null, null).andExpect(status().isCreated()));
            assertThat(created.get("id").asLong()).isPositive();
            assertThat(created.get("text").asText()).isEqualTo("Homework first");
            assertThat(created.get("weekdays")).isEmpty();
            assertThat(created.get("dueToday").asBoolean()).isTrue();

            JsonNode list = readBody(asParent(get("/api/checklist")).andExpect(status().isOk()));
            assertThat(list.findValuesAsText("text")).containsExactly("Homework first");
        }

        @Test
        void weekdaysAndDatesAreKept() throws Exception {
            JsonNode created = readBody(add("Laundry", List.of("SATURDAY"), FRIDAY.toString(), "2026-12-31"));
            assertThat(created.get("weekdays").toString()).isEqualTo("[\"SATURDAY\"]");
            assertThat(created.get("validFrom").asText()).isEqualTo(FRIDAY.toString());
            assertThat(created.get("validUntil").asText()).isEqualTo("2026-12-31");
            assertThat(created.get("dueToday").asBoolean()).as("Friday is not Saturday").isFalse();
        }

        @Test
        void newItemsGoToTheEndAndTheOrderCanBeChanged() throws Exception {
            long a = id(add("Homework", List.of(), null, null));
            long b = id(add("Laundry", List.of(), null, null));
            long c = id(add("Tidy the room", List.of(), null, null));

            asParent(put("/api/checklist/order"), Map.of("ids", List.of(c, a, b))).andExpect(status().isOk());

            assertThat(readBody(asParent(get("/api/checklist"))).findValuesAsText("text"))
                    .containsExactly("Tidy the room", "Homework", "Laundry");
        }

        @Test
        void anOrderThatLeavesItemsOutIsRefused() throws Exception {
            long a = id(add("Homework", List.of(), null, null));
            id(add("Laundry", List.of(), null, null));
            asParent(put("/api/checklist/order"), Map.of("ids", List.of(a))).andExpect(status().isBadRequest());
        }

        @Test
        void anItemCanBeChanged() throws Exception {
            long id = id(add("Homework", List.of(), null, null));
            JsonNode changed = readBody(change(id, "Homework, all of it", List.of("MONDAY", "FRIDAY"), null, null)
                    .andExpect(status().isOk()));
            assertThat(changed.get("text").asText()).isEqualTo("Homework, all of it");
            assertThat(changed.get("weekdays").toString()).isEqualTo("[\"MONDAY\",\"FRIDAY\"]");
        }

        @Test
        void aRemovedItemIsGoneFromTheList() throws Exception {
            long id = id(add("Homework", List.of(), null, null));
            asParent(delete("/api/checklist/" + id)).andExpect(status().isNoContent());
            assertThat(readBody(asParent(get("/api/checklist")))).isEmpty();
            assertThat(currentAsChild().get("checklist")).isEmpty();
        }

        @Test
        void aBadItemIsRefused() throws Exception {
            add("  ", List.of(), null, null).andExpect(status().isBadRequest());
            add("Backwards", List.of(), "2026-10-09", "2026-10-05").andExpect(status().isBadRequest());
            add("Funday", List.of("FUNDAY"), null, null).andExpect(status().isBadRequest());
            add("x".repeat(201), List.of(), null, null).andExpect(status().isBadRequest());
        }

        @Test
        void aMissingItemIsNotFound() throws Exception {
            change(999_999L, "Nothing", List.of(), null, null).andExpect(status().isNotFound());
            asParent(delete("/api/checklist/999999")).andExpect(status().isNotFound());
        }

        @Test
        void everyChangeIsAudited() throws Exception {
            long id = id(add("Homework", List.of(), null, null));
            change(id, "Homework first", List.of(), null, null);
            asParent(put("/api/checklist/order"), Map.of("ids", List.of(id)));
            asParent(delete("/api/checklist/" + id));

            assertThat(jdbc.queryForList(
                    "select action from audit_log where entity = 'CHECKLIST_ITEM' order by id", String.class))
                    .containsExactly("CREATE", "UPDATE", "UPDATE", "DELETE");
        }

        @Test
        void aChildCannotChangeTheChecklist() throws Exception {
            long id = id(add("Homework", List.of(), null, null));
            asChild(get("/api/checklist")).andExpect(status().isForbidden());
            asChild(post("/api/checklist"), Map.of("text", "Nothing", "weekdays", List.of()))
                    .andExpect(status().isForbidden());
            asChild(put("/api/checklist/" + id), Map.of("text", "Nothing", "weekdays", List.of()))
                    .andExpect(status().isForbidden());
            asChild(put("/api/checklist/order"), Map.of("ids", List.of(id))).andExpect(status().isForbidden());
            asChild(delete("/api/checklist/" + id)).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("the child sees what is due today")
    class Due {

        @Test
        void theDashboardCarriesTheItemsDueToday() throws Exception {
            add("Homework", List.of(), null, null);
            add("Laundry", List.of("SATURDAY"), null, null);
            add("Swimming bag", List.of(), FRIDAY.toString(), FRIDAY.toString());
            add("Over already", List.of(), "2026-09-01", "2026-10-01");

            assertThat(currentAsChild().get("checklist").findValuesAsText("text"))
                    .containsExactly("Homework", "Swimming bag");
        }

        @Test
        void tomorrowHasItsOwnList() throws Exception {
            add("Homework", List.of("FRIDAY"), null, null);
            add("Laundry", List.of("SATURDAY"), null, null);
            time.setLocal(SATURDAY, 10, 0);
            assertThat(currentAsChild().get("checklist").findValuesAsText("text")).containsExactly("Laundry");
        }

        @Test
        void theListIsTheSameAfterEveryStart() throws Exception {
            // ticking once does not make it go away: the next start asks again
            long id = id(add("Homework", List.of(), null, null));
            start("FUN", List.of(id)).andExpect(status().isCreated());
            asChild(post("/api/sessions/stop")).andExpect(status().isOk());

            assertThat(currentAsChild().get("checklist").findValuesAsText("text")).containsExactly("Homework");
        }
    }

    @Nested
    @DisplayName("screen time starts only with every item ticked")
    class Gate {

        @Test
        void aStartWithoutTheTicksIsRefusedWithWhatIsLeft() throws Exception {
            long homework = id(add("Homework", List.of(), null, null));
            id(add("Laundry", List.of(), null, null));

            JsonNode error = readBody(start("FUN", List.of(homework)).andExpect(status().isConflict()));
            assertThat(error.get("message").asText()).contains("Laundry");
            assertThat(error.get("details").get("pendingChecklist").findValuesAsText("text"))
                    .containsExactly("Laundry");
            assertThat(jdbc.queryForObject("select count(*) from sessions", Integer.class)).isZero();
        }

        @Test
        void aStartWithEveryTickIsRecordedWithTheTicks() throws Exception {
            long homework = id(add("Homework", List.of(), null, null));
            long laundry = id(add("Laundry", List.of(), null, null));

            JsonNode started = readBody(start("FUN", List.of(laundry, homework)).andExpect(status().isCreated()));

            JsonNode ticks = started.get("checklist");
            assertThat(ticks.findValuesAsText("text")).containsExactly("Homework", "Laundry");
            assertThat(ticks.get(0).get("tickedAt").asText()).isEqualTo(time.instant().toString());
        }

        @Test
        void everyStartAsksAgain() throws Exception {
            long id = id(add("Homework", List.of(), null, null));
            start("FUN", List.of(id)).andExpect(status().isCreated());
            time.advanceMinutes(20);
            asChild(post("/api/sessions/stop")).andExpect(status().isOk());
            time.advanceMinutes(60);

            start("FUN", List.of()).andExpect(status().isConflict());
            start("FUN", List.of(id)).andExpect(status().isCreated());
        }

        @Test
        void bookingByHandNeedsTheTicksToo() throws Exception {
            long id = id(add("Homework", List.of(), null, null));

            asChild(post("/api/sessions/manual"), Map.of("minutes", 30, "deviceId", deviceId("iPad"), "type", "FUN"))
                    .andExpect(status().isConflict());

            JsonNode booked = readBody(asChild(post("/api/sessions/manual"), Map.of("minutes", 30,
                    "deviceId", deviceId("iPad"), "type", "FUN", "checklistItemIds", List.of(id)))
                    .andExpect(status().isCreated()));
            assertThat(booked.get("checklist").findValuesAsText("text")).containsExactly("Homework");
        }

        @Test
        void lookingThingsUpIsNotBlocked() throws Exception {
            add("Homework", List.of(), null, null);
            start("QUICK", List.of()).andExpect(status().isCreated());
        }

        @Test
        void aParentIsNotBlocked() throws Exception {
            add("Homework", List.of(), null, null);
            asParent(post("/api/sessions/start"), Map.of("deviceId", deviceId("iPad"), "type", "FUN"))
                    .andExpect(status().isCreated());
        }

        @Test
        void nothingDueMeansNothingToTick() throws Exception {
            add("Laundry", List.of("SATURDAY"), null, null);
            start("FUN", List.of()).andExpect(status().isCreated());
        }

        @Test
        void aTickForSomethingNotDueIsIgnored() throws Exception {
            long homework = id(add("Homework", List.of(), null, null));
            long laundry = id(add("Laundry", List.of("SATURDAY"), null, null));
            JsonNode started = readBody(start("FUN", List.of(homework, laundry)).andExpect(status().isCreated()));
            assertThat(started.get("checklist").findValuesAsText("text")).containsExactly("Homework");
        }

        @Test
        void theCutOffIsStillTheReasonAfterEight() throws Exception {
            add("Homework", List.of(), null, null);
            time.setLocal(FRIDAY, 20, 30);
            start("FUN", List.of()).andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("what was ticked stays readable")
    class History {

        @Test
        void aTickKeepsTheTextAsItReadThen() throws Exception {
            long id = id(add("Homework", List.of(), null, null));
            start("FUN", List.of(id)).andExpect(status().isCreated());
            change(id, "Homework and reading", List.of(), null, null).andExpect(status().isOk());

            JsonNode entry = currentAsChild().get("todayEntries").get(0);
            assertThat(entry.get("checklist").findValuesAsText("text")).containsExactly("Homework");
        }

        @Test
        void removingAnItemKeepsItsTicks() throws Exception {
            long id = id(add("Homework", List.of(), null, null));
            start("FUN", List.of(id)).andExpect(status().isCreated());
            asParent(delete("/api/checklist/" + id)).andExpect(status().isNoContent());

            assertThat(currentAsChild().get("todayEntries").get(0).get("checklist")).hasSize(1);
        }

        @Test
        void theParentsWeekShowsTheTicksOnEachEntry() throws Exception {
            long id = id(add("Homework", List.of(), null, null));
            start("FUN", List.of(id)).andExpect(status().isCreated());

            JsonNode week = readBody(asParent(get("/api/account/week").param("start", MONDAY.toString())));
            JsonNode friday = week.get("days").get(4).get("entries").get(0);
            assertThat(friday.get("checklist").findValuesAsText("text")).containsExactly("Homework");
        }

        @Test
        void anEntryWithoutTicksHasAnEmptyList() throws Exception {
            start("FUN", List.of()).andExpect(status().isCreated());
            assertThat(currentAsChild().get("todayEntries").get(0).get("checklist")).isEmpty();
        }
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions add(String text, List<String> weekdays, String from, String until) throws Exception {
        return asParent(post("/api/checklist"), body(text, weekdays, from, until));
    }

    private ResultActions change(long id, String text, List<String> weekdays, String from, String until)
            throws Exception {
        return asParent(put("/api/checklist/" + id), body(text, weekdays, from, until));
    }

    private static Map<String, Object> body(String text, List<String> weekdays, String from, String until) {
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("weekdays", weekdays);
        if (from != null) {
            body.put("validFrom", from);
        }
        if (until != null) {
            body.put("validUntil", until);
        }
        return body;
    }

    private ResultActions start(String type, List<Long> ticked) throws Exception {
        return asChild(post("/api/sessions/start"),
                Map.of("deviceId", deviceId("iPad"), "type", type, "checklistItemIds", ticked));
    }

    private long id(ResultActions created) throws Exception {
        return readBody(created).get("id").asLong();
    }
}

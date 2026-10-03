package com.kiwih.screentime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kiwih.screentime.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * The whole application, on a real PostgreSQL, with a clock the test controls.
 * These tests go through the HTTP layer on purpose: the role rules, the status
 * codes and the token are part of what has to work.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiTestBase.TestClockConfig.class)
public abstract class ApiTestBase extends PostgresTestBase {

    /** A Monday, so the week in these tests is 2026-09-28 to 2026-10-04. */
    protected static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);
    protected static final LocalDate FRIDAY = LocalDate.of(2026, 10, 2);
    protected static final LocalDate SATURDAY = LocalDate.of(2026, 10, 3);
    protected static final LocalDate SUNDAY = LocalDate.of(2026, 10, 4);

    protected static final String PARENT_USERNAME = "test-parent";
    protected static final String PARENT_PASSWORD = "test-only-not-a-real-secret";
    protected static final String CHILD_USERNAME = "child";
    protected static final String CHILD_PASSWORD = "child-test-password";

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return new MutableClock(Instant.parse("2026-10-02T14:00:00Z"));
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected Clock clock;

    protected MutableClock time;
    protected String parentToken;
    protected String childToken;

    @BeforeEach
    void setUpApi() throws Exception {
        time = (MutableClock) clock;
        // Friday afternoon, well before the cut off, unless a test says otherwise
        time.setLocal(FRIDAY, 16, 0);

        resetData();
        parentToken = login(PARENT_USERNAME, PARENT_PASSWORD);
        createChild();
        childToken = login(CHILD_USERNAME, CHILD_PASSWORD);
    }

    /** Everything a test writes, removed. The bootstrap parent and the reference data stay. */
    protected void resetData() {
        jdbc.update("delete from weekly_check_devices");
        jdbc.update("delete from adjustments");
        jdbc.update("delete from weekly_checks");
        jdbc.update("delete from sessions");
        jdbc.update("delete from week_flags");
        jdbc.update("delete from audit_log");
        jdbc.update("delete from users where role = 'CHILD'");
        jdbc.update("update settings set value = ? where key = 'weeklyMinutes'", "480");
        jdbc.update("update settings set value = ? where key = 'weekdayCapMinutes'", "60");
        jdbc.update("update settings set value = ? where key = 'weekendCapMinutes'", "120");
        jdbc.update("update settings set value = ? where key = 'quickDailyMinutes'", "15");
        jdbc.update("update settings set value = ? where key = 'cutoffHour'", "20");
        jdbc.update("update settings set value = ? where key = 'bonusMinutes'", "60");
        jdbc.update("update settings set value = ? where key = 'bonusWeekendCapMinutes'", "150");
        jdbc.update("update settings set value = ? where key = 'maxPenaltyMinutes'", "120");
        jdbc.update("update settings set value = ? where key = 'toleranceMinutes'", "10");
        jdbc.update("update settings set value = ? where key = 'manualMaxMinutes'", "240");
        jdbc.update("update settings set value = ? where key = 'deliberatePenaltyMinutes'", "60");
    }

    protected String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("login failed for " + username
                    + ": " + result.getResponse().getStatus() + " " + result.getResponse().getContentAsString());
        }
        return read(result).get("token").asText();
    }

    protected void createChild() throws Exception {
        asParent(post("/api/users"), Map.of(
                "username", CHILD_USERNAME,
                "password", CHILD_PASSWORD,
                "displayName", "Child",
                "role", "CHILD")).andExpect(r -> {
            if (r.getResponse().getStatus() != 201) {
                throw new AssertionError("could not create the child account: "
                        + r.getResponse().getContentAsString());
            }
        });
    }

    // ------------------------------------------------------------- request helpers

    protected ResultActions asParent(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b)
            throws Exception {
        return mvc.perform(b.header("Authorization", "Bearer " + parentToken));
    }

    protected ResultActions asParent(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b,
                                     Object body) throws Exception {
        return mvc.perform(b.header("Authorization", "Bearer " + parentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    protected ResultActions asChild(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b)
            throws Exception {
        return mvc.perform(b.header("Authorization", "Bearer " + childToken));
    }

    protected ResultActions asChild(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b,
                                    Object body) throws Exception {
        return mvc.perform(b.header("Authorization", "Bearer " + childToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode readBody(ResultActions actions) throws Exception {
        return read(actions.andReturn());
    }

    protected Long deviceId(String name) {
        return jdbc.queryForObject("select id from devices where name = ?", Long.class, name);
    }

    protected JsonNode currentAsChild() throws Exception {
        return readBody(asChild(get("/api/account/current")));
    }
}

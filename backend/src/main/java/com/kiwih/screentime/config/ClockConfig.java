package com.kiwih.screentime.config;

import com.kiwih.screentime.rules.WeekCalendar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * The server's clock and calendar, as beans, so that a test can replace the
 * clock and nothing in the application ever calls {@code Instant.now()} on its
 * own. Every timestamp in the database comes from here.
 */
@Configuration
public class ClockConfig {

    @Bean
    public ZoneId appZone(AppProperties properties) {
        return ZoneId.of(properties.getTimezone());
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public WeekCalendar weekCalendar(ZoneId appZone) {
        return new WeekCalendar(appZone);
    }
}

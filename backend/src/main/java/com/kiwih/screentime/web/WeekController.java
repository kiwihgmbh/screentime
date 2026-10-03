package com.kiwih.screentime.web;

import com.kiwih.screentime.rules.SettingsScope;
import com.kiwih.screentime.rules.WeekCalendar;
import com.kiwih.screentime.rules.WeekState;
import com.kiwih.screentime.service.SettingsService;
import com.kiwih.screentime.service.WeekService;
import com.kiwih.screentime.web.dto.WeekFlagsView;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * What a week is: a holiday week or not, and whether the bonus is on. Read
 * only. Holidays come from the holiday periods and the bonus from the weekly
 * check, so there is nothing here to switch by hand.
 */
@RestController
@RequestMapping("/api/weeks")
public class WeekController {

    private final WeekService weeks;
    private final SettingsService settings;
    private final WeekCalendar calendar;

    public WeekController(WeekService weeks, SettingsService settings, WeekCalendar calendar) {
        this.weeks = weeks;
        this.settings = settings;
        this.calendar = calendar;
    }

    @Operation(summary = "The flags of one week")
    @GetMapping("/{weekStart}")
    public WeekFlagsView get(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        LocalDate monday = calendar.weekStartOf(weekStart);
        WeekState state = weeks.state(monday);
        boolean holiday = settings.weekSettings(monday).scope() == SettingsScope.HOLIDAY;
        return new WeekFlagsView(monday, holiday, state.bonusActive());
    }
}

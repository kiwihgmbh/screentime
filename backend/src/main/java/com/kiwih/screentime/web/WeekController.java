package com.kiwih.screentime.web;

import com.kiwih.screentime.rules.WeekCalendar;
import com.kiwih.screentime.rules.WeekState;
import com.kiwih.screentime.security.CurrentUser;
import com.kiwih.screentime.service.WeekService;
import com.kiwih.screentime.web.dto.HolidayRequest;
import com.kiwih.screentime.web.dto.WeekFlagsView;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * The holiday flag. Not in the endpoint table of the specification, but the
 * parent view needs it and a flag nobody can set is not a feature.
 */
@RestController
@RequestMapping("/api/weeks")
public class WeekController {

    private final WeekService weeks;
    private final WeekCalendar calendar;
    private final CurrentUser currentUser;

    public WeekController(WeekService weeks, WeekCalendar calendar, CurrentUser currentUser) {
        this.weeks = weeks;
        this.calendar = calendar;
        this.currentUser = currentUser;
    }

    @Operation(summary = "The flags of one week")
    @GetMapping("/{weekStart}")
    public WeekFlagsView get(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return view(weeks.state(calendar.weekStartOf(weekStart)));
    }

    @Operation(summary = "Mark a week as a holiday week, so the weekend ceiling applies every day")
    @PutMapping("/{weekStart}/holiday")
    public WeekFlagsView setHoliday(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @Valid @RequestBody HolidayRequest request) {
        return view(weeks.setHoliday(calendar.weekStartOf(weekStart),
                request.holiday(), currentUser.require().userId()));
    }

    private static WeekFlagsView view(WeekState state) {
        return new WeekFlagsView(state.weekStart(), state.holiday(), state.bonusActive());
    }
}

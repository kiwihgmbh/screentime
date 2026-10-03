package com.kiwih.screentime.web;

import com.kiwih.screentime.domain.HolidayPeriod;
import com.kiwih.screentime.security.CurrentUser;
import com.kiwih.screentime.service.AccountService;
import com.kiwih.screentime.service.HolidayService;
import com.kiwih.screentime.web.dto.HolidayRequest;
import com.kiwih.screentime.web.dto.HolidayView;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayService holidays;
    private final AccountService accounts;
    private final CurrentUser currentUser;

    public HolidayController(HolidayService holidays, AccountService accounts, CurrentUser currentUser) {
        this.holidays = holidays;
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Holiday periods sharing a day with the range, or all of them")
    @GetMapping
    public List<HolidayView> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Map<Long, String> names = accounts.userNames();
        return holidays.list(from, to).stream().map(p -> view(p, names)).toList();
    }

    @Operation(summary = "Add a holiday period. An overlap with another period is refused with 409.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayView create(@Valid @RequestBody HolidayRequest request) {
        HolidayPeriod saved = holidays.create(request.name(), request.startDate(), request.endDate(),
                currentUser.require().userId());
        return view(saved, accounts.userNames());
    }

    @Operation(summary = "Change a holiday period. An overlap with another period is refused with 409.")
    @PutMapping("/{id}")
    public HolidayView update(@PathVariable Long id, @Valid @RequestBody HolidayRequest request) {
        HolidayPeriod saved = holidays.update(id, request.name(), request.startDate(), request.endDate(),
                currentUser.require().userId());
        return view(saved, accounts.userNames());
    }

    @Operation(summary = "Remove a holiday period")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        holidays.delete(id, currentUser.require().userId());
    }

    private static HolidayView view(HolidayPeriod p, Map<Long, String> names) {
        return new HolidayView(p.getId(), p.getName(), p.getStartDate(), p.getEndDate(),
                (int) ChronoUnit.DAYS.between(p.getStartDate(), p.getEndDate()) + 1,
                p.getCreatedBy() == null ? null : names.get(p.getCreatedBy()), p.getCreatedAt());
    }
}

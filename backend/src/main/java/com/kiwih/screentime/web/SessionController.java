package com.kiwih.screentime.web;

import com.kiwih.screentime.domain.Session;
import com.kiwih.screentime.rules.WeekCalendar;
import com.kiwih.screentime.security.CurrentUser;
import com.kiwih.screentime.service.AccountService;
import com.kiwih.screentime.service.SessionService;
import com.kiwih.screentime.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessions;
    private final AccountService accounts;
    private final CurrentUser currentUser;
    private final WeekCalendar calendar;
    private final Clock clock;

    public SessionController(SessionService sessions, AccountService accounts,
                             CurrentUser currentUser, WeekCalendar calendar, Clock clock) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.currentUser = currentUser;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Operation(summary = "Start a timer. The server sets the start time.")
    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionView start(@Valid @RequestBody StartSessionRequest request) {
        return view(sessions.start(currentUser.require(), request.deviceId(), request.type()));
    }

    @Operation(summary = "Stop the session that is running")
    @PostMapping("/stop")
    public SessionView stop() {
        return view(sessions.stop(currentUser.require()));
    }

    @Operation(summary = "Book time by hand. A child may only book today, before the cut off.")
    @PostMapping("/manual")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionView manual(@Valid @RequestBody ManualSessionRequest request) {
        return view(sessions.bookManually(currentUser.require(), request.deviceId(), request.type(),
                request.minutes(), request.date(), request.note()));
    }

    @Operation(summary = "Entries in a date range. A child sees only their own.")
    @GetMapping
    public List<SessionView> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return accounts.sessions(currentUser.require(), from, to);
    }

    @Operation(summary = "Correct an entry")
    @PutMapping("/{id}")
    public SessionView update(@PathVariable Long id, @Valid @RequestBody UpdateSessionRequest request) {
        return view(sessions.update(currentUser.require(), id, request.minutes(),
                request.deviceId(), request.type(), request.date(), request.note()));
    }

    @Operation(summary = "Delete an entry")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        sessions.delete(currentUser.require(), id);
    }

    @Operation(summary = "The devices time can be booked on")
    @GetMapping("/devices")
    public List<DeviceView> devices() {
        return sessions.activeDevices().stream()
                .map(d -> new DeviceView(d.getId(), d.getName(), d.isActive()))
                .toList();
    }

    private SessionView view(Session session) {
        return accounts.toView(session, accounts.deviceNames(), accounts.userNames(),
                calendar, clock.instant());
    }
}

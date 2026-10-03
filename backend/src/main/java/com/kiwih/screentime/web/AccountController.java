package com.kiwih.screentime.web;

import com.kiwih.screentime.security.CurrentUser;
import com.kiwih.screentime.service.AccountService;
import com.kiwih.screentime.web.dto.AccountView;
import com.kiwih.screentime.web.dto.WeekSummaryView;
import com.kiwih.screentime.web.dto.WeekView;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accounts;
    private final CurrentUser currentUser;

    public AccountController(AccountService accounts, CurrentUser currentUser) {
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Everything the dashboard needs, in one call")
    @GetMapping("/current")
    public AccountView current() {
        return accounts.current(currentUser.require());
    }

    @Operation(summary = "One week, all seven days, with the entries of each")
    @GetMapping("/week")
    public WeekView week(@RequestParam(required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start) {
        return accounts.week(currentUser.require(), start);
    }

    @Operation(summary = "One row per week, most recent first")
    @GetMapping("/history")
    public List<WeekSummaryView> history(@RequestParam(defaultValue = "12") int weeks) {
        return accounts.history(currentUser.require(), weeks);
    }
}

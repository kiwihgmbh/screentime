package com.kiwih.screentime.web;

import com.kiwih.screentime.domain.ReportedDevice;
import com.kiwih.screentime.security.CurrentUser;
import com.kiwih.screentime.service.AccountService;
import com.kiwih.screentime.service.CheckService;
import com.kiwih.screentime.web.dto.AdjustmentView;
import com.kiwih.screentime.web.dto.CheckRequest;
import com.kiwih.screentime.web.dto.CheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/checks")
public class CheckController {

    private final CheckService checks;
    private final AccountService accounts;
    private final CurrentUser currentUser;

    public CheckController(CheckService checks, AccountService accounts, CurrentUser currentUser) {
        this.checks = checks;
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Record the weekly check. A second check for a week replaces the first.")
    @PostMapping
    public CheckResponse save(@Valid @RequestBody CheckRequest request) {
        List<ReportedDevice> reported = request.reported().stream()
                .map(r -> new ReportedDevice(r.deviceId(), r.minutes()))
                .toList();

        CheckService.Result result = checks.save(
                currentUser.require(), request.weekStart(), reported, request.deliberate());

        var deviceNames = accounts.deviceNames();
        var userNames = accounts.userNames();
        AdjustmentView penalty = result.penalty() == null ? null : new AdjustmentView(
                result.penalty().getId(), result.penalty().getWeekStart(),
                result.penalty().getMinutes(), result.penalty().getReason(),
                userNames.get(result.penalty().getCreatedBy()),
                result.penalty().getCreatedAt(), true);

        return new CheckResponse(
                accounts.toCheckView(result.check(), deviceNames),
                result.outcome().moreLoggedThanReported(),
                result.outcome().bonusForFollowingWeek(),
                result.followingWeek(),
                penalty);
    }
}

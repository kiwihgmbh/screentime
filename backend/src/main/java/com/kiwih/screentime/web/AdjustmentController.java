package com.kiwih.screentime.web;

import com.kiwih.screentime.domain.Adjustment;
import com.kiwih.screentime.security.CurrentUser;
import com.kiwih.screentime.service.AccountService;
import com.kiwih.screentime.service.AdjustmentService;
import com.kiwih.screentime.web.dto.AdjustmentRequest;
import com.kiwih.screentime.web.dto.AdjustmentView;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/adjustments")
public class AdjustmentController {

    private final AdjustmentService adjustments;
    private final AccountService accounts;
    private final CurrentUser currentUser;

    public AdjustmentController(AdjustmentService adjustments, AccountService accounts,
                                CurrentUser currentUser) {
        this.adjustments = adjustments;
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Correct a week's budget, with a reason the child can read")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentView create(@Valid @RequestBody AdjustmentRequest request) {
        Adjustment saved = adjustments.create(currentUser.require(),
                request.weekStart(), request.minutes(), request.reason());
        return new AdjustmentView(saved.getId(), saved.getWeekStart(), saved.getMinutes(),
                saved.getReason(), accounts.userNames().get(saved.getCreatedBy()),
                saved.getCreatedAt(), false);
    }

    @Operation(summary = "Remove an adjustment made by hand")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        adjustments.delete(currentUser.require(), id);
    }
}

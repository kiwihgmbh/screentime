package com.kiwih.screentime.web;

import com.kiwih.screentime.security.CurrentUser;
import com.kiwih.screentime.service.SettingsOverviewService;
import com.kiwih.screentime.service.SettingsService;
import com.kiwih.screentime.web.dto.EffectiveSettingsView;
import com.kiwih.screentime.web.dto.SettingsChangeRequest;
import com.kiwih.screentime.web.dto.SettingsView;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settings;
    private final SettingsOverviewService overview;
    private final CurrentUser currentUser;

    public SettingsController(SettingsService settings, SettingsOverviewService overview,
                              CurrentUser currentUser) {
        this.settings = settings;
        this.overview = overview;
        this.currentUser = currentUser;
    }

    @Operation(summary = "Both value sets and the threshold, with their history and the change log")
    @GetMapping
    public SettingsView get() {
        return overview.overview();
    }

    @Operation(summary = "Change values of one scope, from this Monday or next Monday. "
            + "Validated as a whole, for every week the change reaches, before anything is written.")
    @PutMapping
    public SettingsView update(@Valid @RequestBody SettingsChangeRequest request) {
        settings.save(request.scope(), request.values(), request.validFrom(), currentUser.require().userId());
        return overview.overview();
    }

    @Operation(summary = "The values in force for a week, which set won, and the holiday days behind it")
    @GetMapping("/effective")
    public EffectiveSettingsView effective(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        return overview.effective(week != null ? week : settings.today());
    }
}

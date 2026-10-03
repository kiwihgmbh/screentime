package com.kiwih.screentime.web;

import com.kiwih.screentime.security.CurrentUser;
import com.kiwih.screentime.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settings;
    private final CurrentUser currentUser;

    public SettingsController(SettingsService settings, CurrentUser currentUser) {
        this.settings = settings;
        this.currentUser = currentUser;
    }

    @Operation(summary = "The current settings")
    @GetMapping
    public Map<String, String> get() {
        return settings.current().toMap();
    }

    @Operation(summary = "Change settings. The whole set is validated before anything is written.")
    @PutMapping
    public Map<String, String> update(@RequestBody Map<String, String> changes) {
        return settings.update(changes, currentUser.require().userId()).toMap();
    }
}

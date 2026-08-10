package com.codeguard.controller;

import com.codeguard.ai.AiProvider;
import com.codeguard.dto.SettingsResponse;
import com.codeguard.dto.UpdateSettingsRequest;
import com.codeguard.model.AppSettings;
import com.codeguard.service.AppSettingsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final AppSettingsService settingsService;
    private final Map<String, AiProvider> providers;

    public SettingsController(AppSettingsService settingsService, Map<String, AiProvider> providers) {
        this.settingsService = settingsService;
        this.providers = providers;
    }

    @GetMapping
    public SettingsResponse get() {
        AppSettings settings = settingsService.getSettings();
        boolean overridden = settings.getActiveAiProviderOverride() != null && !settings.getActiveAiProviderOverride().isBlank();
        return new SettingsResponse(
                settingsService.resolveActiveProvider(), overridden,
                settings.getCustomInstructions() == null ? "" : settings.getCustomInstructions(),
                List.copyOf(providers.keySet()));
    }

    @PostMapping
    public SettingsResponse update(@RequestBody UpdateSettingsRequest req) {
        AppSettings settings = settingsService.getSettings();
        settings.setActiveAiProviderOverride(req.getActiveAiProviderOverride());
        settings.setCustomInstructions(req.getCustomInstructions());
        settingsService.save(settings);
        return get();
    }
}
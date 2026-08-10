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
        boolean providerOverridden = notBlank(settings.getActiveAiProviderOverride());

        return new SettingsResponse(
                settingsService.resolveActiveProvider(),
                providerOverridden,
                orEmpty(settings.getCustomInstructions()),
                List.copyOf(providers.keySet()),
                maskToken(settingsService.resolveGithubToken()),
                notBlank(settings.getGithubTokenOverride()),
                notBlank(settings.getWebhookSecretOverride()),
                orEmpty(settings.getAllowedRepos()));
    }

    @PostMapping
    public SettingsResponse update(@RequestBody UpdateSettingsRequest req) {
        AppSettings settings = settingsService.getSettings();

        settings.setActiveAiProviderOverride(req.getActiveAiProviderOverride());
        settings.setCustomInstructions(req.getCustomInstructions());
        settings.setAllowedRepos(req.getAllowedRepos());

        // Null means "field wasn't touched, leave as-is" - lets the frontend submit the form
        // without needing to know/resubmit the current secret value (which we never send back in full).
        if (req.getGithubTokenOverride() != null) {
            settings.setGithubTokenOverride(req.getGithubTokenOverride());
        }
        if (req.getWebhookSecretOverride() != null) {
            settings.setWebhookSecretOverride(req.getWebhookSecretOverride());
        }

        settingsService.save(settings);
        return get();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Never send the full token to the browser - only enough to confirm which one is active. */
    private String maskToken(String token) {
        if (token == null || token.isBlank()) return "";
        if (token.length() <= 8) return "****";
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
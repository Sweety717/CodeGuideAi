package com.codeguard.service;

import com.codeguard.model.AppSettings;
import com.codeguard.repository.AppSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AppSettingsService {

    private final AppSettingsRepository repository;

    /** Fallback when no override has been saved via the UI - the original application.properties value. */
    @Value("${ai.provider:openai}")
    private String defaultProviderFromProperties;

    public AppSettingsService(AppSettingsRepository repository) {
        this.repository = repository;
    }

    public AppSettings getSettings() {
        return repository.findAll().stream().findFirst().orElseGet(() -> repository.save(new AppSettings()));
    }

    public AppSettings save(AppSettings settings) {
        return repository.save(settings);
    }

    /** Resolves the active provider: UI override if set, otherwise application.properties. */
    public String resolveActiveProvider() {
        AppSettings settings = getSettings();
        String override = settings.getActiveAiProviderOverride();
        if (override != null && !override.isBlank()) {
            return override.toLowerCase().trim();
        }
        return defaultProviderFromProperties.toLowerCase().trim();
    }

    public String resolveCustomInstructions() {
        String instructions = getSettings().getCustomInstructions();
        return instructions == null ? "" : instructions.trim();
    }
}
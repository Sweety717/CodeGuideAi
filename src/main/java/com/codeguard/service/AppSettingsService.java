package com.codeguard.service;

import com.codeguard.model.AppSettings;
import com.codeguard.repository.AppSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AppSettingsService {

    private final AppSettingsRepository repository;

    @Value("${ai.provider:openai}")
    private String defaultProviderFromProperties;

    @Value("${github.token:}")
    private String defaultGithubTokenFromProperties;

    @Value("${github.webhook-secret:}")
    private String defaultWebhookSecretFromProperties;

    public AppSettingsService(AppSettingsRepository repository) {
        this.repository = repository;
    }

    public AppSettings getSettings() {
        return repository.findAll().stream().findFirst().orElseGet(() -> repository.save(new AppSettings()));
    }

    public AppSettings save(AppSettings settings) {
        return repository.save(settings);
    }

    public String resolveActiveProvider() {
        String override = getSettings().getActiveAiProviderOverride();
        return (override != null && !override.isBlank()) ? override.toLowerCase().trim() : defaultProviderFromProperties.toLowerCase().trim();
    }

    public String resolveCustomInstructions() {
        String v = getSettings().getCustomInstructions();
        return v == null ? "" : v.trim();
    }

    public String resolveGithubToken() {
        String override = getSettings().getGithubTokenOverride();
        return (override != null && !override.isBlank()) ? override.trim() : defaultGithubTokenFromProperties;
    }

    public String resolveWebhookSecret() {
        String override = getSettings().getWebhookSecretOverride();
        return (override != null && !override.isBlank()) ? override.trim() : defaultWebhookSecretFromProperties;
    }

    /** Empty set means "no restriction" - every repo is allowed. */
    public Set<String> resolveAllowedRepos() {
        String raw = getSettings().getAllowedRepos();
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
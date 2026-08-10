package com.codeguard.dto;

import java.util.List;

public record SettingsResponse(
        String activeProvider,
        boolean isProviderOverridden,
        String customInstructions,
        List<String> availableProviders,
        String githubTokenMasked,
        boolean hasGithubTokenOverride,
        boolean hasWebhookSecretOverride,
        String allowedRepos
) {
}
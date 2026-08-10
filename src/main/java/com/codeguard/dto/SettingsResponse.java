package com.codeguard.dto;

import java.util.List;

public record SettingsResponse(String activeProvider, boolean isOverridden, String customInstructions, List<String> availableProviders) {
}
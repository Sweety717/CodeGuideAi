package com.codeguard.dto;

public class UpdateSettingsRequest {
    /** Empty/null clears the override and falls back to application.properties. */
    private String activeAiProviderOverride;
    private String customInstructions;

    public String getActiveAiProviderOverride() { return activeAiProviderOverride; }
    public void setActiveAiProviderOverride(String v) { this.activeAiProviderOverride = v; }
    public String getCustomInstructions() { return customInstructions; }
    public void setCustomInstructions(String v) { this.customInstructions = v; }
}
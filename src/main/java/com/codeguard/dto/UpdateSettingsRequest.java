package com.codeguard.dto;

public class UpdateSettingsRequest {
    private String activeAiProviderOverride;
    private String customInstructions;
    /** Null/omitted = leave the current token unchanged. Empty string = clear the override. */
    private String githubTokenOverride;
    private String webhookSecretOverride;
    private String allowedRepos;

    public String getActiveAiProviderOverride() { return activeAiProviderOverride; }
    public void setActiveAiProviderOverride(String v) { this.activeAiProviderOverride = v; }
    public String getCustomInstructions() { return customInstructions; }
    public void setCustomInstructions(String v) { this.customInstructions = v; }
    public String getGithubTokenOverride() { return githubTokenOverride; }
    public void setGithubTokenOverride(String v) { this.githubTokenOverride = v; }
    public String getWebhookSecretOverride() { return webhookSecretOverride; }
    public void setWebhookSecretOverride(String v) { this.webhookSecretOverride = v; }
    public String getAllowedRepos() { return allowedRepos; }
    public void setAllowedRepos(String v) { this.allowedRepos = v; }
}
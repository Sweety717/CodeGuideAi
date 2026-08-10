package com.codeguard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.Getter;
import lombok.Setter;

/**
 * Single-row settings table (this product is single-team/self-hosted, same
 * pattern used elsewhere in this codebase). Lets the AI provider and custom
 * review instructions be changed from the UI without editing
 * application.properties or restarting the app.
 */
@Entity
@Getter
@Setter
public class AppSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Overrides ai.provider from application.properties when set. Null/blank
     * means "use whatever application.properties says" (the original
     * behavior, kept as the default so existing deployments don't change
     * behavior until someone actually opens Settings and picks something).
     */
    private String activeAiProviderOverride;

    /** Appended to the review system prompt when non-blank - lets a team encode their own review rules/priorities. */
    @Lob
    @Column(length = 8000)
    private String customInstructions;

    /** Overrides github.token from application.properties when set. Editable from Settings so it doesn't require a restart to rotate. */
    private String githubTokenOverride;

    /** Overrides github.webhook-secret from application.properties when set. */
    private String webhookSecretOverride;

    /**
     * Comma-separated "owner/repo" allowlist. When non-blank, the webhook
     * handler ignores PR events from any repo not in this list - lets a
     * buyer point one deployment's webhook at an org without reviewing
     * every repo in it. Blank means "review anything the webhook is sent for".
     */
    @Lob
    @Column(length = 4000)
    private String allowedRepos;
}
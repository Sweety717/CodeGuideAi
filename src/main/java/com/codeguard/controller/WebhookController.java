package com.codeguard.controller;

import com.codeguard.service.GitHubClient;
import com.codeguard.service.ReviewOrchestrationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Receives GitHub's "pull_request" webhook events. Point your repo's webhook
 * at POST {your-domain}/webhook/github with content type application/json
 * and the same secret configured in github.webhook-secret.
 */
@RestController
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private static final Set<String> REVIEW_TRIGGER_ACTIONS = Set.of("opened", "reopened", "synchronize");

    private final GitHubClient gitHubClient;
    private final ReviewOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebhookController(GitHubClient gitHubClient, ReviewOrchestrationService orchestrationService) {
        this.gitHubClient = gitHubClient;
        this.orchestrationService = orchestrationService;
    }

    @PostMapping("/webhook/github")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String eventType) {

        if (!gitHubClient.isValidSignature(rawBody, signature)) {
            log.warn("Rejected webhook with invalid or missing signature.");
            return ResponseEntity.status(401).body("Invalid signature");
        }

        if (!"pull_request".equals(eventType)) {
            // GitHub also sends "ping" on webhook setup, and other event types if
            // you subscribed to more than pull_request - acknowledge and ignore.
            return ResponseEntity.ok("Ignored event type: " + eventType);
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body("Could not parse payload");
        }

        String action = payload.path("action").asText("");
        if (!REVIEW_TRIGGER_ACTIONS.contains(action)) {
            return ResponseEntity.ok("Ignored action: " + action);
        }

        String repoFullName = payload.path("repository").path("full_name").asText("");
        int prNumber = payload.path("number").asInt(0);

        if (repoFullName.isBlank() || prNumber == 0) {
            return ResponseEntity.badRequest().body("Missing repository or PR number");
        }

        log.info("Queuing AI review for {}#{}", repoFullName, prNumber);
        orchestrationService.reviewAsync(repoFullName, prNumber, true);

        // Respond immediately - GitHub expects a fast response and will retry/flag
        // the webhook as failing if it takes too long. The actual review runs
        // async and posts its comment when done.
        return ResponseEntity.ok("Review queued");
    }
}

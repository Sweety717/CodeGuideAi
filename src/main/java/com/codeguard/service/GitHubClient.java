package com.codeguard.service;

import com.codeguard.dto.PullRequestFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the GitHub REST API (v2022-11-28). Token and webhook
 * secret are resolved fresh on every call via AppSettingsService, so a
 * Settings-page change takes effect immediately without a restart.
 */
@Service
public class GitHubClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);
    private static final String API_BASE = "https://api.github.com";

    private final AppSettingsService settingsService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GitHubClient(AppSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public boolean isConfigured() {
        String token = settingsService.resolveGithubToken();
        return token != null && !token.isBlank();
    }

    public boolean isValidSignature(String rawBody, String signatureHeader) {
        String webhookSecret = settingsService.resolveWebhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("No webhook secret configured (application.properties or Settings) - rejecting webhook.");
            return false;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computedHex = "sha256=" + HexFormat.of().formatHex(computed);
            return java.security.MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.error("Webhook signature verification failed", ex);
            return false;
        }
    }

    public JsonNode getPullRequest(String repoFullName, int prNumber) {
        String url = API_BASE + "/repos/" + repoFullName + "/pulls/" + prNumber;
        return parse(exchange(url, HttpMethod.GET, null));
    }

    public List<PullRequestFile> getPullRequestFiles(String repoFullName, int prNumber) {
        String url = API_BASE + "/repos/" + repoFullName + "/pulls/" + prNumber + "/files?per_page=100";
        JsonNode root = parse(exchange(url, HttpMethod.GET, null));

        List<PullRequestFile> files = new ArrayList<>();
        for (JsonNode n : root) {
            files.add(new PullRequestFile(
                    n.path("filename").asText(""),
                    n.path("status").asText(""),
                    n.path("additions").asInt(0),
                    n.path("deletions").asInt(0),
                    n.path("patch").asText("")));
        }
        return files;
    }

    public void postIssueComment(String repoFullName, int prNumber, String markdownBody) {
        String url = API_BASE + "/repos/" + repoFullName + "/issues/" + prNumber + "/comments";
        exchange(url, HttpMethod.POST, Map.of("body", markdownBody));
    }

    private String exchange(String url, HttpMethod method, Object body) {
        String token = settingsService.resolveGithubToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("No GitHub token configured - set github.token in application.properties or add one in Settings.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");

        try {
            return restTemplate.exchange(url, method, new HttpEntity<>(body, headers), String.class).getBody();
        } catch (RestClientException ex) {
            log.error("GitHub API call failed: {} {}", method, url, ex);
            throw new IllegalStateException("GitHub API call failed: " + ex.getMessage(), ex);
        }
    }

    private JsonNode parse(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not parse GitHub API response.", ex);
        }
    }
}
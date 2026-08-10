package com.codeguard.service;

import com.codeguard.dto.PullRequestFile;
import com.codeguard.dto.ReviewResult;
import com.codeguard.dto.ReviewResult.Finding;
import com.codeguard.model.ReviewedPullRequest;
import com.codeguard.repository.ReviewedPullRequestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReviewOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ReviewOrchestrationService.class);
    private static final Map<String, String> SEVERITY_EMOJI = Map.of(
            "Critical", "\uD83D\uDD34", "High", "\uD83D\uDFE0", "Medium", "\uD83D\uDFE1",
            "Low", "\uD83D\uDD35", "Info", "\u26AA");
    private static final Map<String, String> VERDICT_EMOJI = Map.of(
            "Safe to Merge", "\u2705", "Merge After Fixes", "\u26A0\uFE0F", "Do Not Merge", "\u274C");

    private final GitHubClient gitHubClient;
    private final CodeReviewService codeReviewService;
    private final ReviewedPullRequestRepository repository;
    private final AppSettingsService settingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReviewOrchestrationService(GitHubClient gitHubClient, CodeReviewService codeReviewService,
                                      ReviewedPullRequestRepository repository, AppSettingsService settingsService) {
        this.gitHubClient = gitHubClient;
        this.codeReviewService = codeReviewService;
        this.repository = repository;
        this.settingsService = settingsService;
    }

    @Async
    public void reviewAsync(String repoFullName, int prNumber, boolean postComment) {
        try {
            reviewSync(repoFullName, prNumber, postComment);
        } catch (Exception ex) {
            log.error("Async review failed for {}#{}", repoFullName, prNumber, ex);
        }
    }

    /** True if this repo is allowed to be reviewed given the current Settings allowlist (empty allowlist = everything allowed). */
    public boolean isRepoAllowed(String repoFullName) {
        Set<String> allowed = settingsService.resolveAllowedRepos();
        return allowed.isEmpty() || allowed.contains(repoFullName);
    }

    public ReviewedPullRequest reviewSync(String repoFullName, int prNumber, boolean postComment) {
        long startedAt = System.currentTimeMillis();

        JsonNode pr = gitHubClient.getPullRequest(repoFullName, prNumber);
        String title = pr.path("title").asText("");
        String description = pr.path("body").asText("");
        String author = pr.path("user").path("login").asText("");
        String url = pr.path("html_url").asText("");
        String headSha = pr.path("head").path("sha").asText("");

        List<PullRequestFile> files = gitHubClient.getPullRequestFiles(repoFullName, prNumber);
        int totalAdditions = files.stream().mapToInt(PullRequestFile::additions).sum();
        int totalDeletions = files.stream().mapToInt(PullRequestFile::deletions).sum();

        ReviewResult result = codeReviewService.review(title, description, files);
        long durationMs = System.currentTimeMillis() - startedAt;

        ReviewedPullRequest entity = new ReviewedPullRequest();
        entity.setRepoFullName(repoFullName);
        entity.setPrNumber(prNumber);
        entity.setPrTitle(title);
        entity.setPrAuthor(author);
        entity.setPrUrl(url);
        entity.setHeadSha(headSha);
        entity.setRiskLevel(result.riskLevel());
        entity.setMergeRecommendation(result.mergeRecommendation());
        entity.setConfidence(result.confidence());
        entity.setOverallScore(result.overallScore());
        entity.setOverallSummary(result.overallSummary());
        entity.setFindingCount(result.findings().size());
        entity.setFilesChanged(files.size());
        entity.setTotalAdditions(totalAdditions);
        entity.setTotalDeletions(totalDeletions);
        entity.setReviewDurationMs(durationMs);

        try {
            entity.setFindingsJson(objectMapper.writeValueAsString(result));
        } catch (Exception ex) {
            log.error("Failed to serialize review result", ex);
        }

        if (postComment) {
            try {
                gitHubClient.postIssueComment(repoFullName, prNumber, formatMarkdownComment(result, repoFullName, headSha));
                entity.setCommentPosted(true);
            } catch (Exception ex) {
                log.error("Failed to post review comment to {}#{}", repoFullName, prNumber, ex);
                entity.setCommentPosted(false);
            }
        }

        return repository.save(entity);
    }

    public String formatMarkdownComment(ReviewResult result, String repoFullName, String headSha) {
        StringBuilder sb = new StringBuilder();
        sb.append("## \uD83E\uDD16 CodeGuard AI Review\n\n");
        sb.append("### ").append(VERDICT_EMOJI.getOrDefault(result.mergeRecommendation(), "")).append(" ")
                .append(result.mergeRecommendation().toUpperCase()).append("\n\n");
        sb.append("**Quality score:** ").append(result.overallScore()).append("/10 &nbsp;\u00B7&nbsp; ")
                .append("**Risk:** ").append(result.riskLevel()).append(" &nbsp;\u00B7&nbsp; ")
                .append("**Confidence:** ").append(result.confidence()).append("%\n\n");
        sb.append(result.overallSummary()).append("\n\n");

        if (!result.findings().isEmpty()) {
            sb.append("### Findings\n\n");
            for (Finding f : result.findings()) {
                String emoji = SEVERITY_EMOJI.getOrDefault(f.severity(), "\u26AA");
                sb.append(emoji).append(" **").append(f.severity()).append(" \u00B7 ").append(f.category()).append("**");
                if (f.title() != null && !f.title().isBlank()) {
                    sb.append(" \u2014 ").append(f.title());
                }
                sb.append("\n");

                String fileRef = "`" + f.file() + "`";
                if (f.lineNumber() > 0 && repoFullName != null && !repoFullName.isBlank() && headSha != null && !headSha.isBlank()) {
                    String link = "https://github.com/" + repoFullName + "/blob/" + headSha + "/" + f.file() + "#L" + f.lineNumber();
                    fileRef = "[" + f.file() + " (line " + f.lineNumber() + ")](" + link + ")";
                } else if (f.lineHint() != null && !f.lineHint().isBlank()) {
                    fileRef += " (" + f.lineHint() + ")";
                }
                sb.append(fileRef).append("\n\n").append(f.comment());

                if (f.codeSnippet() != null && !f.codeSnippet().isBlank()) {
                    sb.append("\n\n```\n").append(f.codeSnippet()).append("\n```");
                }
                if (f.suggestion() != null && !f.suggestion().isBlank()) {
                    sb.append("\n\n> **Suggestion:** ").append(f.suggestion());
                }
                sb.append("\n\n---\n\n");
            }
        } else {
            sb.append("No issues found. \u2705\n\n");
        }

        if (result.positives() != null && !result.positives().isEmpty()) {
            sb.append("### What's good\n\n");
            for (String p : result.positives()) {
                sb.append("- ").append(p).append("\n");
            }
            sb.append("\n");
        }

        sb.append("<sub>Generated by CodeGuard AI \u2014 self-hosted, review it critically like any AI output.</sub>");
        return sb.toString();
    }
}
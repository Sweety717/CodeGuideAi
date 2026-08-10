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

@Service
public class ReviewOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ReviewOrchestrationService.class);
    private static final Map<String, String> SEVERITY_EMOJI = Map.of(
            "Critical", "\uD83D\uDD34", "High", "\uD83D\uDFE0", "Medium", "\uD83D\uDFE1",
            "Low", "\uD83D\uDD35", "Info", "\u26AA");

    private final GitHubClient gitHubClient;
    private final CodeReviewService codeReviewService;
    private final ReviewedPullRequestRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReviewOrchestrationService(GitHubClient gitHubClient, CodeReviewService codeReviewService,
                                      ReviewedPullRequestRepository repository) {
        this.gitHubClient = gitHubClient;
        this.codeReviewService = codeReviewService;
        this.repository = repository;
    }

    @Async
    public void reviewAsync(String repoFullName, int prNumber, boolean postComment) {
        try {
            reviewSync(repoFullName, prNumber, postComment);
        } catch (Exception ex) {
            log.error("Async review failed for {}#{}", repoFullName, prNumber, ex);
        }
    }

    public ReviewedPullRequest reviewSync(String repoFullName, int prNumber, boolean postComment) {
        JsonNode pr = gitHubClient.getPullRequest(repoFullName, prNumber);
        String title = pr.path("title").asText("");
        String description = pr.path("body").asText("");
        String author = pr.path("user").path("login").asText("");
        String url = pr.path("html_url").asText("");

        List<PullRequestFile> files = gitHubClient.getPullRequestFiles(repoFullName, prNumber);
        int totalAdditions = files.stream().mapToInt(PullRequestFile::additions).sum();
        int totalDeletions = files.stream().mapToInt(PullRequestFile::deletions).sum();

        ReviewResult result = codeReviewService.review(title, description, files);

        ReviewedPullRequest entity = new ReviewedPullRequest();
        entity.setRepoFullName(repoFullName);
        entity.setPrNumber(prNumber);
        entity.setPrTitle(title);
        entity.setPrAuthor(author);
        entity.setPrUrl(url);
        entity.setRiskLevel(result.riskLevel());
        entity.setOverallScore(result.overallScore());
        entity.setOverallSummary(result.overallSummary());
        entity.setFindingCount(result.findings().size());
        entity.setFilesChanged(files.size());
        entity.setTotalAdditions(totalAdditions);
        entity.setTotalDeletions(totalDeletions);

        try {
            entity.setFindingsJson(objectMapper.writeValueAsString(result));
        } catch (Exception ex) {
            log.error("Failed to serialize review result", ex);
        }

        if (postComment) {
            try {
                gitHubClient.postIssueComment(repoFullName, prNumber, formatMarkdownComment(result));
                entity.setCommentPosted(true);
            } catch (Exception ex) {
                log.error("Failed to post review comment to {}#{}", repoFullName, prNumber, ex);
                entity.setCommentPosted(false);
            }
        }

        return repository.save(entity);
    }

    public String formatMarkdownComment(ReviewResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("## \uD83E\uDD16 CodeGuard AI Review\n\n");
        sb.append("**Quality score:** ").append(result.overallScore()).append("/10 &nbsp;\u00B7&nbsp; ")
                .append("**Risk:** ").append(result.riskLevel()).append("\n\n");
        sb.append(result.overallSummary()).append("\n\n");

        if (!result.findings().isEmpty()) {
            sb.append("### Findings\n\n");
            for (Finding f : result.findings()) {
                String emoji = SEVERITY_EMOJI.getOrDefault(f.severity(), "\u26AA");
                sb.append(emoji).append(" **").append(f.severity()).append(" \u00B7 ").append(f.category()).append("**");
                if (f.title() != null && !f.title().isBlank()) {
                    sb.append(" \u2014 ").append(f.title());
                }
                sb.append("\n`").append(f.file()).append("`");
                if (f.lineHint() != null && !f.lineHint().isBlank()) {
                    sb.append(" (").append(f.lineHint()).append(")");
                }
                sb.append("\n\n").append(f.comment());
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
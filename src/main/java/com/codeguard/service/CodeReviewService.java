package com.codeguard.service;

import com.codeguard.ai.AiProvider;
import com.codeguard.dto.PullRequestFile;
import com.codeguard.dto.ReviewResult;
import com.codeguard.dto.ReviewResult.Finding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CodeReviewService {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewService.class);

    private static final int MAX_PATCH_CHARS_PER_FILE = 4000;
    private static final int MAX_FILES_REVIEWED = 25;

    private final Map<String, AiProvider> providers;
    private final AppSettingsService settingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CodeReviewService(Map<String, AiProvider> providers, AppSettingsService settingsService) {
        this.providers = providers;
        this.settingsService = settingsService;
    }

    /** Resolved fresh on every call - reflects the latest Settings UI choice without needing a restart. */
    public String activeProviderName() {
        return settingsService.resolveActiveProvider();
    }

    public ReviewResult review(String prTitle, String prDescription, List<PullRequestFile> files) {
        String providerName = activeProviderName();
        AiProvider provider = providers.get(providerName);
        if (provider == null) {
            throw new IllegalStateException(
                    "Unknown AI provider '" + providerName + "'. Valid options: "
                            + String.join(", ", providers.keySet()));
        }

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(prTitle, prDescription, files);

        try {
            String content = provider.complete(systemPrompt, userPrompt, true);
            return parseResult(content);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("AI provider '{}' call failed during review", providerName, ex);
            throw new IllegalStateException(
                    "Couldn't reach the AI service (" + providerName + "). Please try again.", ex);
        }
    }

    private String buildSystemPrompt() {
        String base = """
                You are a senior software engineer performing a thorough, constructive code
                review of a GitHub pull request diff. Respond with ONLY a single JSON object,
                no markdown fences, no commentary, matching EXACTLY this shape:

                {
                  "overallScore": <integer 0-10, overall code quality of this PR - 10 is excellent>,
                  "riskLevel": <one of: "Low", "Medium", "High", "Critical">,
                  "overallSummary": "<2-4 sentence summary of what this PR does and your overall assessment>",
                  "findings": [
                    {
                      "file": "<the file path this finding is about>",
                      "severity": <one of: "Critical", "High", "Medium", "Low", "Info">,
                      "category": <one of: "Bug", "Security", "Performance", "Style", "Best Practice">,
                      "title": "<short issue name, e.g. 'Potential SQL Injection' or 'Missing null check'>",
                      "lineHint": "<approximate location in words, e.g. 'in the new validateInput() method' - diff line numbers shift, describe location, don't just give a number>",
                      "comment": "<what the issue is and why it matters>",
                      "suggestion": "<a concrete fix or improvement>",
                      "codeSnippet": "<a short (1-5 line) excerpt from the diff that shows the issue - exact code, not paraphrased. Empty string if not applicable.>"
                    }
                  ],
                  "positives": [<0-4 short strings noting things done well - good tests, clean naming, etc. Omit if genuinely nothing stands out.>]
                }

                Focus on: bugs, security vulnerabilities (injection, auth, secrets, unsafe
                deserialization), performance issues, error handling gaps, and violations of
                the language/framework's established best practices. Do NOT nitpick pure
                formatting that a linter would already catch (indentation, trailing
                whitespace) unless it affects readability significantly. Only flag issues
                actually visible in the diff shown - never assume context you can't see.
                If the diff is clean, return an empty findings array, a high overallScore,
                and say so in the summary.
                """;

        String custom = settingsService.resolveCustomInstructions();
        if (!custom.isEmpty()) {
            base += "\n\nTEAM-SPECIFIC REVIEW RULES (apply these in addition to the above, and prioritize "
                    + "them when they conflict with general best practice - this is this team's own standard):\n"
                    + custom + "\n";
        }
        return base;
    }

    private String buildUserPrompt(String prTitle, String prDescription, List<PullRequestFile> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("PR TITLE: ").append(prTitle).append("\n");
        if (prDescription != null && !prDescription.isBlank()) {
            sb.append("PR DESCRIPTION:\n").append(prDescription).append("\n");
        }
        sb.append("\nFILES CHANGED (unified diff format, may be truncated for very large files):\n\n");

        int fileCount = 0;
        for (PullRequestFile f : files) {
            if (fileCount >= MAX_FILES_REVIEWED) {
                sb.append("... (").append(files.size() - fileCount).append(" additional files omitted for prompt size)\n");
                break;
            }
            if (f.patch() == null || f.patch().isBlank()) {
                sb.append("--- ").append(f.filename()).append(" (").append(f.status())
                        .append(", no text diff available - binary or too large) ---\n\n");
                continue;
            }
            String patch = f.patch();
            if (patch.length() > MAX_PATCH_CHARS_PER_FILE) {
                patch = patch.substring(0, MAX_PATCH_CHARS_PER_FILE) + "\n... (diff truncated for length)";
            }
            sb.append("--- ").append(f.filename()).append(" (").append(f.status()).append(") ---\n");
            sb.append(patch).append("\n\n");
            fileCount++;
        }
        return sb.toString();
    }

    private ReviewResult parseResult(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);

            int overallScore = clamp(root.path("overallScore").asInt(5), 0, 10);
            String riskLevel = root.path("riskLevel").asText("Medium");
            String overallSummary = root.path("overallSummary").asText("");

            List<Finding> findings = new ArrayList<>();
            for (JsonNode n : root.path("findings")) {
                findings.add(new Finding(
                        n.path("file").asText(""),
                        n.path("severity").asText("Info"),
                        n.path("category").asText("Best Practice"),
                        n.path("title").asText(""),
                        n.path("lineHint").asText(""),
                        n.path("comment").asText(""),
                        n.path("suggestion").asText(""),
                        n.path("codeSnippet").asText("")));
            }

            List<String> positives = new ArrayList<>();
            root.path("positives").forEach(n -> positives.add(n.asText()));

            return new ReviewResult(overallScore, riskLevel, overallSummary, findings, positives);
        } catch (Exception ex) {
            log.error("Failed to parse AI review response as JSON: {}", content, ex);
            throw new IllegalStateException("The AI returned an unexpected response format. Please try again.", ex);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
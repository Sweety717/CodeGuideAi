package com.codeguard.dto;

import java.util.List;

public record ReviewResult(
        int overallScore,          // 0-10, quality score
        String riskLevel,          // Low | Medium | High | Critical
        String mergeRecommendation, // "Safe to Merge" | "Merge After Fixes" | "Do Not Merge"
        int confidence,             // 0-100, how confident the AI is in this assessment
        String overallSummary,
        List<Finding> findings,
        List<String> positives
) {
    public record Finding(
            String file,
            String severity,     // Critical | High | Medium | Low | Info
            String category,     // Bug | Security | Performance | Style | Best Practice
            String title,        // short one-line issue name, e.g. "Potential SQL Injection"
            String lineHint,     // approximate location in words - diff line numbers shift
            int lineNumber,      // best-effort actual line number in the new file, 0 if unknown - derived from the diff's @@ hunk header
            String comment,      // full explanation
            String suggestion,   // concrete fix
            String codeSnippet   // short relevant excerpt from the diff, empty if not applicable
    ) {}
}
package com.codeguard.dto;

import java.util.List;

public record ReviewResult(
        int overallScore,      // 0-10, quality score
        String riskLevel,      // Low | Medium | High | Critical
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
            String comment,      // full explanation
            String suggestion,   // concrete fix
            String codeSnippet   // short relevant excerpt from the diff, empty if not applicable
    ) {}
}
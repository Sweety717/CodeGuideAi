package com.codeguard.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class ReviewedPullRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String repoFullName; // e.g. "acme/backend-service"

    private int prNumber;

    private String prTitle;

    private String prAuthor;

    private String prUrl;

    private String riskLevel; // Low | Medium | High | Critical

    private int overallScore; // 0-10

    private String mergeRecommendation; // "Safe to Merge" | "Merge After Fixes" | "Do Not Merge"

    private int confidence; // 0-100

    private long reviewDurationMs;

    /** The PR's head commit SHA - used to build permanent GitHub blob links (blob/{sha}/{file}#L{line}). */
    private String headSha;

    private int filesChanged;

    private int totalAdditions;

    private int totalDeletions;

    @Lob
    @Column(length = 4000)
    private String overallSummary;

    /** Full structured findings, serialized as JSON, so the exact past review can be redisplayed as-is. */
    @Lob
    @Column(length = 30000)
    private String findingsJson;

    private int findingCount;

    private boolean commentPosted;

    private LocalDateTime createdAt = LocalDateTime.now();
}
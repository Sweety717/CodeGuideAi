package com.codeguard.dto;

import jakarta.validation.constraints.NotBlank;

public class ManualReviewRequest {

    @NotBlank
    private String repoFullName; // "owner/repo"

    private int prNumber;

    private boolean postComment = true;

    public String getRepoFullName() { return repoFullName; }
    public void setRepoFullName(String repoFullName) { this.repoFullName = repoFullName; }
    public int getPrNumber() { return prNumber; }
    public void setPrNumber(int prNumber) { this.prNumber = prNumber; }
    public boolean isPostComment() { return postComment; }
    public void setPostComment(boolean postComment) { this.postComment = postComment; }
}

package com.codeguard.dto;

public record PullRequestFile(String filename, String status, int additions, int deletions, String patch) {
}

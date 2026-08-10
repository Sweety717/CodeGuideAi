package com.codeguard.repository;

import com.codeguard.model.ReviewedPullRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewedPullRequestRepository extends JpaRepository<ReviewedPullRequest, Long> {

    List<ReviewedPullRequest> findAllByOrderByCreatedAtDesc();
}

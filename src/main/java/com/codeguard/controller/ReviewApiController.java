package com.codeguard.controller;

import com.codeguard.dto.ConfigResponse;
import com.codeguard.dto.ErrorResponse;
import com.codeguard.dto.ManualReviewRequest;
import com.codeguard.dto.ReviewResult;
import com.codeguard.model.ReviewedPullRequest;
import com.codeguard.repository.ReviewedPullRequestRepository;
import com.codeguard.service.CodeReviewService;
import com.codeguard.service.ReviewOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ReviewApiController {

    private final ReviewedPullRequestRepository repository;
    private final ReviewOrchestrationService orchestrationService;
    private final CodeReviewService codeReviewService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReviewApiController(ReviewedPullRequestRepository repository,
                               ReviewOrchestrationService orchestrationService,
                               CodeReviewService codeReviewService) {
        this.repository = repository;
        this.orchestrationService = orchestrationService;
        this.codeReviewService = codeReviewService;
    }

    @GetMapping("/config")
    public ConfigResponse config() {
        return new ConfigResponse(codeReviewService.activeProviderName());
    }

    @GetMapping("/reviews")
    public List<ReviewedPullRequest> listReviews() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/reviews/{id}")
    public ResponseEntity<?> getReview(@PathVariable Long id) {
        return repository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(new ErrorResponse("Review not found.")));
    }

    @GetMapping("/reviews/{id}/markdown")
    public ResponseEntity<?> downloadMarkdown(@PathVariable Long id) {
        var reviewOpt = repository.findById(id);
        if (reviewOpt.isEmpty()) {
            return ResponseEntity.status(404).body(new ErrorResponse("Review not found."));
        }
        ReviewedPullRequest review = reviewOpt.get();
        try {
            ReviewResult result = objectMapper.readValue(review.getFindingsJson(), ReviewResult.class);
            String markdown = orchestrationService.formatMarkdownComment(result, review.getRepoFullName(), review.getHeadSha());

            String filename = (review.getRepoFullName().replace("/", "_") + "_PR" + review.getPrNumber() + ".md");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.valueOf("text/markdown"))
                    .body(markdown);
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(new ErrorResponse("Could not build the Markdown export."));
        }
    }

    @PostMapping("/reviews/manual")
    public ResponseEntity<?> manualReview(@Valid @RequestBody ManualReviewRequest req) {
        try {
            ReviewedPullRequest result = orchestrationService.reviewSync(
                    req.getRepoFullName(), req.getPrNumber(), req.isPostComment());
            return ResponseEntity.ok(result);
        } catch (IllegalStateException ex) {
            return ResponseEntity.internalServerError().body(new ErrorResponse(ex.getMessage()));
        }
    }
}
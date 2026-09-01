package com.fundit.project.presentation.controller;

import com.fundit.project.application.supporterreview.SupporterReviewService;
import com.fundit.project.presentation.dto.common.ListResponse;
import com.fundit.project.presentation.dto.supporterreview.SupporterReviewCreateRequest;
import com.fundit.project.presentation.dto.supporterreview.SupporterReviewIdResponse;
import com.fundit.project.presentation.dto.supporterreview.SupporterReviewResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/projects/{projectId}/reviews")
public class SupporterReviewController {

    private final SupporterReviewService supporterReviewService;

    @GetMapping
    public ListResponse<SupporterReviewResponse> list(@PathVariable UUID projectId) {
        return ListResponse.of(supporterReviewService.list(projectId).stream()
                .map(SupporterReviewResponse::from)
                .toList());
    }

    @PostMapping
    public ResponseEntity<SupporterReviewIdResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody SupporterReviewCreateRequest request) {

        var review = supporterReviewService.create(projectId, request.fundingId(), request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(new SupporterReviewIdResponse(review.getId()));
    }
}

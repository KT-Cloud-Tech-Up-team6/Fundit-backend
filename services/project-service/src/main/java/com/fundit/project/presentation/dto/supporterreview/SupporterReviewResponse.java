package com.fundit.project.presentation.dto.supporterreview;

import com.fundit.project.application.supporterreview.SupporterReviewService.ReviewWithAuthor;

import java.time.Instant;

public record SupporterReviewResponse(Long reviewId,
                                      String memberNickname,
                                      String content,
                                      Instant createdAt) {

    public static SupporterReviewResponse from(ReviewWithAuthor source) {
        return new SupporterReviewResponse(
                source.review().getId(),
                source.nickname(),
                source.review().getContent(),
                source.review().getCreatedAt());
    }
}

package com.fundit.project.presentation.dto.project;

import com.fundit.project.domain.project.ReviewDecision;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * rejectReason/fundingStartAt/fundingDeadline은 decision 값에 따라 필수 여부가 갈려서
 * 애노테이션 대신 application 계층(ProjectReviewService)에서 검증한다.
 */
public record ReviewRequest(@NotNull ReviewDecision decision,
                            String rejectReason,
                            Instant fundingStartAt,
                            Instant fundingDeadline) {
}

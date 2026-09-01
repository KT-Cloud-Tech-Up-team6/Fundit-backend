package com.fundit.project.presentation.dto.project;

import com.fundit.project.application.project.ProjectQueryService.ProjectDetail;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProjectDetailResponse(UUID projectId,
                                    UUID sellerId,
                                    String title,
                                    String categoryMajor,
                                    String categoryMinor,
                                    Long goalAmount,
                                    long currentAmount,
                                    int achievementRate,
                                    int participantCount,
                                    Instant fundingStartAt,
                                    Instant fundingDeadline,
                                    ProjectStatus status,
                                    Map<String, Object> introContent,
                                    RefundPolicy refundPolicy) {

    public record RefundPolicy(boolean simpleRefundDisabled) {
    }

    public static ProjectDetailResponse from(ProjectDetail detail) {
        Project project = detail.project();
        return new ProjectDetailResponse(
                project.getPublicId(),
                project.getSellerId(),
                project.getTitle(),
                project.getCategoryMajor(),
                project.getCategoryMinor(),
                project.getGoalAmount(),
                detail.stats().currentAmount(),
                project.achievementRate(detail.stats().currentAmount()),
                detail.stats().participantCount(),
                project.getFundingStartAt(),
                project.getFundingDeadline(),
                project.getStatus(),
                project.getIntroContent(),
                new RefundPolicy(detail.simpleRefundDisabled()));
    }
}

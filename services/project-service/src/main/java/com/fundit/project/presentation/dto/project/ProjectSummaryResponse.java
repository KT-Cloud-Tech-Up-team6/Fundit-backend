package com.fundit.project.presentation.dto.project;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectStatus;

import java.time.Instant;
import java.util.UUID;

public record ProjectSummaryResponse(UUID projectId,
                                     String title,
                                     String thumbnailUrl,
                                     ProjectStatus status,
                                     Instant createdAt,
                                     Instant fundingDeadline,
                                     Integer dDay) {

    public static ProjectSummaryResponse from(Project project, Instant now) {
        return new ProjectSummaryResponse(
                project.getPublicId(),
                project.getTitle(),
                project.getThumbnailImageUrl(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getFundingDeadline(),
                project.dDay(now));
    }
}

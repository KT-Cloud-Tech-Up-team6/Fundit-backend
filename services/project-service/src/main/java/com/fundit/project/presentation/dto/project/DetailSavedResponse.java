package com.fundit.project.presentation.dto.project;

import com.fundit.project.domain.project.Project;

import java.time.Instant;
import java.util.UUID;

public record DetailSavedResponse(UUID projectId, Instant savedAt) {

    public static DetailSavedResponse from(Project project) {
        return new DetailSavedResponse(project.getPublicId(), project.getUpdatedAt());
    }
}

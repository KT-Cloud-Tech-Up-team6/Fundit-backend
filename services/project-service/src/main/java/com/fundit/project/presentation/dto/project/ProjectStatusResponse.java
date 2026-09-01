package com.fundit.project.presentation.dto.project;

import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectStatus;

import java.util.UUID;

/** 생성·기본정보 수정·검수 처리처럼 식별자와 상태만 돌려주면 되는 응답. */
public record ProjectStatusResponse(UUID projectId, ProjectStatus status) {

    public static ProjectStatusResponse from(Project project) {
        return new ProjectStatusResponse(project.getPublicId(), project.getStatus());
    }
}

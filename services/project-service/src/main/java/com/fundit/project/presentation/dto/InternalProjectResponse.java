package com.fundit.project.presentation.dto;

import com.fundit.project.application.project.ProjectInternalQueryService.ProjectSnapshot;

import java.util.UUID;

/** 내부 전용 — fulfillment-service가 호출하는 프로젝트 소유권/식별자 조회 응답. */
public record InternalProjectResponse(UUID sellerId, UUID publicId) {

    public static InternalProjectResponse from(ProjectSnapshot snapshot) {
        return new InternalProjectResponse(snapshot.sellerId(), snapshot.publicId());
    }
}

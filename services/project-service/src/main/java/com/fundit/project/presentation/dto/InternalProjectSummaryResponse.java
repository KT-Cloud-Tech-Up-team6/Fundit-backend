package com.fundit.project.presentation.dto;

import com.fundit.project.application.project.ProjectInternalQueryService.ProjectSummarySnapshot;

import java.util.UUID;

/** 내부 전용 — order-service 주문 목록(V03)이 배치로 호출하는 프로젝트 요약 조회 응답. */
public record InternalProjectSummaryResponse(UUID projectId, String title, String thumbnailUrl,
                                              String sellerDisplayName) {

    public static InternalProjectSummaryResponse from(ProjectSummarySnapshot snapshot) {
        return new InternalProjectSummaryResponse(snapshot.publicId(), snapshot.title(), snapshot.thumbnailUrl(),
                snapshot.sellerDisplayName());
    }
}

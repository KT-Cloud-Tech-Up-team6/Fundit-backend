package com.fundit.project.presentation.controller;

import com.fundit.project.application.project.ProjectInternalQueryService;
import com.fundit.project.presentation.dto.InternalProjectResponse;
import com.fundit.project.presentation.dto.InternalProjectSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * fulfillment-service/order-service가 호출하는 내부 전용 API(판매자 소유권 검증/알림 수신자 조회,
 * 주문 목록 배치 요약). 내부 전용 보호는 {@code infrastructure.security.InternalEndpointConfig}에
 * 등록한 {@link com.fundit.common.webmvc.auth.InternalEndpoint} 빈이 담당한다.
 */
@RestController
@RequiredArgsConstructor
public class InternalProjectController {

    private final ProjectInternalQueryService projectInternalQueryService;

    @GetMapping("/internal/projects/{projectId}")
    public InternalProjectResponse getProject(@PathVariable Long projectId) {
        return InternalProjectResponse.from(projectInternalQueryService.getSnapshot(projectId));
    }

    /** order-service 주문 목록(V03) 배치 요약 조회 — 건별 호출(N+1) 방지용. */
    @GetMapping("/internal/projects/summaries")
    public List<InternalProjectSummaryResponse> getSummaries(@RequestParam List<UUID> ids) {
        return projectInternalQueryService.getSummaries(ids).stream()
                .map(InternalProjectSummaryResponse::from)
                .toList();
    }
}

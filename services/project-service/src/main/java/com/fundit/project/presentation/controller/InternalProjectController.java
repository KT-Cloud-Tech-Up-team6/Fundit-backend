package com.fundit.project.presentation.controller;

import com.fundit.project.application.project.ProjectInternalQueryService;
import com.fundit.project.presentation.dto.InternalProjectResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * fulfillment-service가 호출하는 내부 전용 API(판매자 소유권 검증/알림 수신자 조회).
 * 내부 전용 보호는 {@code infrastructure.security.InternalEndpointConfig}에 등록한
 * {@link com.fundit.common.webmvc.auth.InternalEndpoint} 빈이 담당한다.
 */
@RestController
@RequiredArgsConstructor
public class InternalProjectController {

    private final ProjectInternalQueryService projectInternalQueryService;

    @GetMapping("/internal/projects/{projectId}")
    public InternalProjectResponse getProject(@PathVariable Long projectId) {
        return InternalProjectResponse.from(projectInternalQueryService.getSnapshot(projectId));
    }
}

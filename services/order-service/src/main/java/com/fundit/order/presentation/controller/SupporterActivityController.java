package com.fundit.order.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.application.catalog.ProjectOwnershipClient;
import com.fundit.order.application.supporter.SupporterActivityService;
import com.fundit.order.presentation.dto.PageResponse;
import com.fundit.order.presentation.dto.SupporterActivityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * ORDER-001 — 서포터 활동 목록 조회. 인증 불필요(공통).
 *
 * <p>cross-service ID 통일(#69) 이후 project-service는 UUID(publicId)만 노출하지만, 이 엔드포인트는
 * 아직 v1(Long) 계약을 유지한다 — 요청마다 project-service 내부 API로 UUID를 먼저 해석한 뒤
 * UUID 기반 서비스 레이어를 호출한다. 계약 자체는 안 바뀌어(Long 그대로) 별도 /api/v2가 필요 없다.
 */
@RestController
@RequiredArgsConstructor
public class SupporterActivityController {

    private final SupporterActivityService supporterActivityService;
    private final ProjectOwnershipClient projectOwnershipClient;

    @GetMapping("/api/v1/projects/{projectId}/supporters")
    public PageResponse<SupporterActivityResponse> list(@PathVariable Long projectId,
                                                          @PageableDefault(size = 20) Pageable pageable) {
        UUID projectPublicId = projectOwnershipClient.findPublicId(projectId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return PageResponse.from(supporterActivityService.list(projectPublicId, pageable)
                .map(SupporterActivityResponse::from));
    }
}

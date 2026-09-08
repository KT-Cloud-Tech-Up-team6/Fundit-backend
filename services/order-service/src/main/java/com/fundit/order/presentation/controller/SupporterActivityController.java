package com.fundit.order.presentation.controller;

import com.fundit.order.application.supporter.SupporterActivityService;
import com.fundit.order.presentation.dto.PageResponse;
import com.fundit.order.presentation.dto.SupporterActivityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** ORDER-001 — 서포터 활동 목록 조회. 인증 불필요(공통). */
@RestController
@RequiredArgsConstructor
public class SupporterActivityController {

    private final SupporterActivityService supporterActivityService;

    @GetMapping("/api/v1/projects/{projectId}/supporters")
    public PageResponse<SupporterActivityResponse> list(@PathVariable Long projectId,
                                                          @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(supporterActivityService.list(projectId, pageable)
                .map(SupporterActivityResponse::from));
    }
}

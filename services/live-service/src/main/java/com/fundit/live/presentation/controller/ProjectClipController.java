package com.fundit.live.presentation.controller;

import com.fundit.live.application.highlight.HighlightService;
import com.fundit.live.presentation.dto.PageResponse;
import com.fundit.live.presentation.dto.ProjectClipResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 프로젝트 단위 공개 숏 클립(구매자 LIVE 체크 탭). 인증 불필요.
 *
 * <p>{@link LiveHighlightController}와 분리한 이유: 그쪽은 경로가 {@code /{liveId}/highlights}로 방송에
 * 묶여 있고, 이건 방송을 가로질러 프로젝트로 모은다. 리터럴 경로라 {@code /{liveId}/...}보다 먼저 매칭된다.
 * 반복 방송이 쌓이면 끝없이 늘어나는 목록이라 페이지로 나눈다.
 */
@RestController
@RequiredArgsConstructor
public class ProjectClipController {

    private final HighlightService highlightService;

    @GetMapping("/api/v1/lives/highlights")
    public PageResponse<ProjectClipResponse> findPublicClips(@RequestParam UUID projectId,
                                                             @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(highlightService.findPublicClips(projectId, pageable).map(ProjectClipResponse::from));
    }
}

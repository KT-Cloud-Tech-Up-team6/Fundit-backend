package com.fundit.live.presentation.controller;

import com.fundit.live.application.highlight.HighlightService;
import com.fundit.live.presentation.dto.ProjectClipResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 프로젝트 단위 공개 숏 클립(구매자 LIVE 체크 탭). 인증 불필요.
 *
 * <p>{@link LiveHighlightController}와 분리한 이유: 그쪽은 경로가 {@code /{liveId}/highlights}로 방송에
 * 묶여 있고, 이건 방송을 가로질러 프로젝트로 모은다. 리터럴 경로라 {@code /{liveId}/...}보다 먼저 매칭된다.
 * 방송당 클립이 최대 3개라 페이지네이션은 두지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class ProjectClipController {

    private final HighlightService highlightService;

    @GetMapping("/api/v1/lives/highlights")
    public List<ProjectClipResponse> findPublicClips(@RequestParam UUID projectId) {
        return highlightService.findPublicClips(projectId).stream().map(ProjectClipResponse::from).toList();
    }
}

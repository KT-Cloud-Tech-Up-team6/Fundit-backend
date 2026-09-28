package com.fundit.search.presentation.controller;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.search.application.home.HomeFeedQueryService;
import com.fundit.search.application.live.LiveCardClient;
import com.fundit.search.application.live.LiveCardClient.LiveCard;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectCardProjection;
import com.fundit.search.presentation.dto.ContentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
@Slf4j
public class HomeController {

    private final HomeFeedQueryService homeFeedQueryService;
    private final LiveCardClient liveCardClient;

    @GetMapping("/feed")
    public ContentResponse<ProjectCardProjection> getFeed(@RequestParam(required = false) Integer size) {
        return new ContentResponse<>(homeFeedQueryService.getHomeFeed(size));
    }

    /**
     * SEARCH-002. live-service {@code GET /api/v1/lives/banner}를 그대로 프록시한다 —
     * 방송 중인 LIVE가 없으면 빈 배열이고, 영역 미노출은 프론트가 처리한다.
     *
     * <p>live-service 장애를 503으로 올리지 않는 이유: 홈은 피드와 LIVE 섹션이 한 화면에 같이
     * 뜬다. LIVE 하나 때문에 홈 전체가 내려가면 피드까지 사라지므로, 이 섹션만 비우고 나머지를
     * 살린다. 검색 LIVE 탭(SEARCH-006)은 그 페이지 전체가 LIVE라 반대로 503을 그대로 올린다.
     */
    @GetMapping("/lives")
    public ContentResponse<LiveCard> getLives() {
        try {
            return new ContentResponse<>(liveCardClient.findBanner());
        } catch (DependencyFailureException e) {
            log.warn("live-service 배너 조회 실패 — 홈 LIVE 섹션을 빈 목록으로 응답한다", e);
            return new ContentResponse<>(List.of());
        }
    }
}

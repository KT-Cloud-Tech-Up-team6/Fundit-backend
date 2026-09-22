package com.fundit.search.presentation.controller;

import com.fundit.search.application.home.HomeFeedQueryService;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectCardProjection;
import com.fundit.search.presentation.dto.ContentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeFeedQueryService homeFeedQueryService;

    @GetMapping("/feed")
    public ContentResponse<ProjectCardProjection> getFeed(@RequestParam(required = false) Integer size) {
        return new ContentResponse<>(homeFeedQueryService.getHomeFeed(size));
    }

    /**
     * SEARCH-002. live-service는 이미 끝났지만 {@code live_documents}를 채울 컨슈머가 아직 없어
     * 항상 빈 배열을 반환하는 스텁이다(SearchERD.md 5-③). 홈 배너는 색인이 없어도
     * live-service {@code GET /api/v1/lives/banner}로 이미 해결되므로, 검색 LIVE 탭이 실제
     * 필요해질 때(SEARCH-006) 컨슈머를 붙이면서 같이 교체한다(YAGNI).
     */
    @GetMapping("/lives")
    public ContentResponse<Object> getLives() {
        return new ContentResponse<>(List.of());
    }
}

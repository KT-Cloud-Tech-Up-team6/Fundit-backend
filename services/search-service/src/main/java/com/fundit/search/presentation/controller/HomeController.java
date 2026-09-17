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
     * SEARCH-002. live-service가 아직 개발에 착수하지 않아 항상 빈 배열을 반환하는 스텁이다
     * (SearchDomainFunctionalSpec.md SEARCH-002 검토의견). live-service 착수 후 실제 조회로 교체할 것.
     */
    @GetMapping("/lives")
    public ContentResponse<Object> getLives() {
        return new ContentResponse<>(List.of());
    }
}

package com.fundit.search.presentation.controller;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.webmvc.auth.CurrentUser;
import com.fundit.common.webmvc.auth.LoginUser;
import com.fundit.search.application.search.PopularKeywordQueryService;
import com.fundit.search.application.search.ProjectSearchService;
import com.fundit.search.application.search.RecentKeywordService;
import com.fundit.search.application.search.SellerSearchService;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectCardProjection;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectSearchSubTab;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectSortType;
import com.fundit.search.infrastructure.persistence.sellersummary.query.SellerCardProjection;
import com.fundit.search.presentation.SearchPageRequests;
import com.fundit.search.presentation.dto.ContentResponse;
import com.fundit.search.presentation.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final ProjectSearchService projectSearchService;
    private final RecentKeywordService recentKeywordService;
    private final PopularKeywordQueryService popularKeywordQueryService;
    private final SellerSearchService sellerSearchService;

    /**
     * SEARCH-005/008. 미로그인도 검색 가능(선택적 인증) — 로그인 회원만 최근검색어가 자동 저장되므로
     * @LoginUser 대신 헤더를 직접 읽는다(project-service CommunityController와 동일 패턴).
     */
    @GetMapping("/projects")
    public PageResponse<ProjectCardProjection> searchProjects(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "ONGOING") ProjectSearchSubTab subTab,
            @RequestParam(defaultValue = "POPULAR") ProjectSortType sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) String accountIdHeader) {
        UUID memberId = parseOrNull(accountIdHeader);
        var result = projectSearchService.search(keyword, subTab, sort, memberId, SearchPageRequests.of(page, size));
        return PageResponse.from(result);
    }

    /**
     * SEARCH-006. live-service는 이미 끝났지만 {@code live_documents}를 채울 컨슈머가 아직 없어
     * 항상 빈 결과를 반환하는 스텁이다(SearchERD.md 5-③) — 실제 필요해질 때 컨슈머를 붙인다(YAGNI).
     * SEARCH-005와 동일한 키워드/로그 검증은 하지 않는다.
     */
    @GetMapping("/lives")
    public PageResponse<Object> searchLives(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return new PageResponse<>(List.of(), page, size, 0, 0, false);
    }

    /** SEARCH-009. 본인 최근 검색어만 조회한다(security.md S4) — member_id는 항상 @LoginUser에서 주입. */
    @GetMapping("/recent-keywords")
    public ContentResponse<RecentKeywordService.RecentKeywordItem> getRecentKeywords(
            @LoginUser CurrentUser user, @RequestParam(required = false) Integer size) {
        return new ContentResponse<>(recentKeywordService.getRecentKeywords(user.id(), size));
    }

    /** SEARCH-009 개별 삭제. Idempotent — 존재하지 않는 키워드도 204. */
    @DeleteMapping("/recent-keywords/{keyword}")
    public ResponseEntity<Void> deleteRecentKeyword(@LoginUser CurrentUser user, @PathVariable String keyword) {
        recentKeywordService.deleteKeyword(user.id(), keyword);
        return ResponseEntity.noContent().build();
    }

    /** SEARCH-009 전체 삭제. */
    @DeleteMapping("/recent-keywords")
    public ResponseEntity<Void> deleteAllRecentKeywords(@LoginUser CurrentUser user) {
        recentKeywordService.deleteAllKeywords(user.id());
        return ResponseEntity.noContent().build();
    }

    /** SEARCH-007. */
    @GetMapping("/sellers")
    public PageResponse<SellerCardProjection> searchSellers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = sellerSearchService.search(keyword, SearchPageRequests.of(page, size));
        return PageResponse.from(result);
    }

    /** SEARCH-010. 집계 배치가 한 번도 돌지 않았으면 빈 배열을 반환한다 — 에러 아님. */
    @GetMapping("/popular-keywords")
    public ContentResponse<PopularKeywordQueryService.PopularKeywordItem> getPopularKeywords() {
        return new ContentResponse<>(popularKeywordQueryService.getPopularKeywords());
    }

    private UUID parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

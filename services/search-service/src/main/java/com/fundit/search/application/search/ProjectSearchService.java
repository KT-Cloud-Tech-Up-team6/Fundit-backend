package com.fundit.search.application.search;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectCardProjection;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectSearchSubTab;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectSortType;
import com.fundit.search.infrastructure.persistence.recentkeyword.RecentSearchKeywordJpaRepository;
import com.fundit.search.infrastructure.persistence.searchquerylog.SearchQueryLogJpaEntity;
import com.fundit.search.infrastructure.persistence.searchquerylog.SearchQueryLogJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * SEARCH-005/008. 상품 탭 통합검색 실행 + 로그인 회원 최근검색어 자동 저장(부수 효과).
 *
 * <p>이 메서드에는 일부러 @Transactional을 걸지 않는다 — 검색 로그 적재와 최근검색어 upsert/정리는
 * 서로 원자적일 필요가 없는 독립된 부수 효과라서, 하나로 묶으면 Postgres가 한 트랜잭션 내
 * 이후 문장을 전부 실패시키는 특성 때문에 최근검색어 저장 실패가 검색 로그 적재까지 끌고 내려간다.
 * 각 리포지토리 호출은 Spring Data 프록시가 제공하는 자기 자신의 트랜잭션 경계를 그대로 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectSearchService {

    private static final int RECENT_KEYWORD_LIMIT = 10;

    private final ProjectDocumentJpaRepository projectDocumentJpaRepository;
    private final SearchQueryLogJpaRepository searchQueryLogJpaRepository;
    private final RecentSearchKeywordJpaRepository recentSearchKeywordJpaRepository;

    public Page<ProjectCardProjection> search(
            String keyword, ProjectSearchSubTab subTab, ProjectSortType sortType, UUID memberId, PageRequest pageRequest) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "keyword는 1자 이상이어야 합니다.");
        }

        var statuses = subTab == ProjectSearchSubTab.ENDED
                ? List.of(ProjectDocumentStatus.SUCCEEDED, ProjectDocumentStatus.FAILED)
                : List.of(ProjectDocumentStatus.ONGOING);
        var result = projectDocumentJpaRepository.searchByKeyword(
                keyword, statuses, pageRequest.withSort(sortType.toSort()));

        searchQueryLogJpaRepository.save(SearchQueryLogJpaEntity.builder()
                .memberId(memberId)
                .keyword(keyword)
                .resultCount((int) result.getTotalElements())
                .build());

        if (memberId != null) {
            saveRecentKeyword(memberId, keyword);
        }
        return result;
    }

    /** "부가 기능 실패가 주 기능을 막지 않는다"(SearchDomainApiSpec.md #5) — 저장 실패는 검색 응답에 영향을 주지 않는다. */
    private void saveRecentKeyword(UUID memberId, String keyword) {
        try {
            recentSearchKeywordJpaRepository.upsert(memberId, keyword);
            recentSearchKeywordJpaRepository.deleteExceedingLimit(memberId, RECENT_KEYWORD_LIMIT);
        } catch (RuntimeException e) {
            log.warn("최근 검색어 저장 실패 - memberId={}, keyword={}", memberId, keyword, e);
        }
    }
}

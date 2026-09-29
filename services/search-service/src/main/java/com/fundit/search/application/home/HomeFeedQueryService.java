package com.fundit.search.application.home;

import com.fundit.search.application.SearchPageLimits;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectCardProjection;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** SEARCH-001. 색인이 비어 있으면(콜드 스타트, SEARCH-011 미구현) 빈 배열을 반환한다 — 에러 아님. */
@Service
@RequiredArgsConstructor
public class HomeFeedQueryService {

    private static final int DEFAULT_SIZE = SearchPageLimits.DEFAULT_SIZE;
    private static final int MAX_SIZE = SearchPageLimits.MAX_SIZE;

    private final ProjectDocumentJpaRepository projectDocumentJpaRepository;

    @Transactional(readOnly = true)
    public List<ProjectCardProjection> getHomeFeed(ProjectSortType sort, Integer size) {
        int limit = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        if (sort == null || sort == ProjectSortType.POPULAR) {
            return projectDocumentJpaRepository.findByStatusAndDeletedAtIsNullOrderByParticipantCountDescWishCountDesc(
                    ProjectDocumentStatus.ONGOING, PageRequest.of(0, limit));
        }
        // 마감일·등록일이 같은 프로젝트끼리 순서가 요청마다 바뀌지 않도록 id를 마지막 기준으로 둔다
        Sort order = sort.toSort().and(Sort.by(Sort.Order.asc("projectId")));
        return projectDocumentJpaRepository.findByStatusAndDeletedAtIsNullAndFundingDeadlineGreaterThanEqual(
                ProjectDocumentStatus.ONGOING, Instant.now(), PageRequest.of(0, limit, order));
    }
}

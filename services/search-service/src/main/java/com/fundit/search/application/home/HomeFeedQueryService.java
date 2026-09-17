package com.fundit.search.application.home;

import com.fundit.search.application.SearchPageLimits;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;
import com.fundit.search.infrastructure.persistence.projectdocument.query.ProjectCardProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** SEARCH-001. 색인이 비어 있으면(콜드 스타트, SEARCH-011 미구현) 빈 배열을 반환한다 — 에러 아님. */
@Service
@RequiredArgsConstructor
public class HomeFeedQueryService {

    private static final int DEFAULT_SIZE = SearchPageLimits.DEFAULT_SIZE;
    private static final int MAX_SIZE = SearchPageLimits.MAX_SIZE;

    private final ProjectDocumentJpaRepository projectDocumentJpaRepository;

    @Transactional(readOnly = true)
    public List<ProjectCardProjection> getHomeFeed(Integer size) {
        int limit = (size == null || size < 1) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return projectDocumentJpaRepository.findByStatusAndDeletedAtIsNullOrderByParticipantCountDescWishCountDesc(
                ProjectDocumentStatus.ONGOING, PageRequest.of(0, limit));
    }
}

package com.fundit.search.application.projectdocument;

import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SEARCH-014. 같은 (projectId, memberId) 이벤트가 재수신돼도 search_wish_stat_members 가드로
 * 중복 가감을 막는다(project-service ProjectStatsService.applyProjectWished와 동일 패턴).
 */
@Service
@RequiredArgsConstructor
public class ProjectDocumentWishCountSyncService implements WishEventListener {

    private final ProjectDocumentJpaRepository projectDocumentJpaRepository;

    @Override
    @Transactional
    public void onProjectWished(ProjectWishedEvent event) {
        if (projectDocumentJpaRepository.insertWishMemberIfAbsent(event.projectId(), event.memberId()) > 0) {
            projectDocumentJpaRepository.incrementWishCount(event.projectId());
        }
    }

    @Override
    @Transactional
    public void onProjectUnwished(ProjectUnwishedEvent event) {
        if (projectDocumentJpaRepository.deleteWishMember(event.projectId(), event.memberId()) > 0) {
            projectDocumentJpaRepository.decrementWishCount(event.projectId());
        }
    }
}

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
        requireIndexed(event.projectId());
        if (projectDocumentJpaRepository.insertWishMemberIfAbsent(event.projectId(), event.memberId()) > 0) {
            projectDocumentJpaRepository.incrementWishCount(event.projectId());
        }
    }

    @Override
    @Transactional
    public void onProjectUnwished(ProjectUnwishedEvent event) {
        requireIndexed(event.projectId());
        if (projectDocumentJpaRepository.deleteWishMember(event.projectId(), event.memberId()) > 0) {
            projectDocumentJpaRepository.decrementWishCount(event.projectId());
        }
    }

    /**
     * 가드 테이블을 먼저 만지면 재시도 때 UNIQUE로 스킵되어 찜수가 영영 반영되지 않는다.
     * 색인이 생길 때까지 예외로 보류하고, upsert 시 가드 행 수로 wish_count를 재구성한다.
     */
    private void requireIndexed(Long projectId) {
        if (!projectDocumentJpaRepository.existsById(projectId)) {
            throw new SearchIndexNotReadyException(projectId);
        }
    }
}

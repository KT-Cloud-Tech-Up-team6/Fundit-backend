package com.fundit.search.application.projectdocument;

import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SEARCH-012. 색인에 없는 projectId(SEARCH-011 이벤트가 아직 도착하지 않은 경우)는
 * {@link SearchIndexNotReadyException}으로 보류해 Kafka가 재시도·DLT로 처리하게 한다.
 */
@Service
@RequiredArgsConstructor
public class ProjectDocumentStatusSyncService implements FundingStatusEventListener {

    private final ProjectDocumentJpaRepository projectDocumentJpaRepository;

    @Override
    @Transactional
    public void onFundingSucceeded(FundingSucceededEvent event) {
        updateStatus(event.projectId(), ProjectDocumentStatus.SUCCEEDED);
    }

    @Override
    @Transactional
    public void onFundingGoalFailed(FundingGoalFailedEvent event) {
        updateStatus(event.projectId(), ProjectDocumentStatus.FAILED);
    }

    private void updateStatus(Long projectId, ProjectDocumentStatus status) {
        int updated = projectDocumentJpaRepository.updateStatus(projectId, status);
        if (updated == 0) {
            throw new SearchIndexNotReadyException(projectId);
        }
    }
}

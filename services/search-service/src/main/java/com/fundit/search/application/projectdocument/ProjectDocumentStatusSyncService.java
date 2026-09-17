package com.fundit.search.application.projectdocument;

import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentJpaRepository;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SEARCH-012. 색인에 없는 projectId(SEARCH-011 이벤트가 아직 도착하지 않은 경우)를 수신하면
 * 갱신 행이 0건이라는 뜻이다 — 예외를 던지지 않고 운영 로그로 확인 대상만 남긴다
 * (SearchDomainFunctionalSpec.md SEARCH-012 예외 처리, order-service FixedBackOff(0,0)과 동일 정책).
 */
@Slf4j
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
            log.warn("색인에 없는 projectId에 대한 상태 갱신 이벤트 수신 — projectId={}, status={}", projectId, status);
        }
    }
}

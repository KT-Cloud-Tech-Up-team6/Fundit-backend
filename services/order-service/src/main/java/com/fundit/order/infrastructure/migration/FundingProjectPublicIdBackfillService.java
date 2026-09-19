package com.fundit.order.infrastructure.migration;

import com.fundit.order.application.catalog.ProjectOwnershipClient;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaEntity;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * cross-service ID 통일(#69) 백필 — 레거시 {@code project_id}(Long)만 있고
 * {@code project_public_id}(UUID)가 비어 있는 펀딩 행을 project-service의 기존 내부 API
 * ({@code GET /internal/projects/{projectId}})로 채운다. 행 하나씩 별도 트랜잭션으로 처리해
 * 중간에 실패해도 이미 처리된 행은 유지되고, 재실행하면 남은 행만 다시 시도한다(idempotent).
 */
@Service
@RequiredArgsConstructor
public class FundingProjectPublicIdBackfillService {

    private final FundingJpaRepository fundingJpaRepository;
    private final ProjectOwnershipClient projectOwnershipClient;

    @Transactional(readOnly = true)
    public List<Long> findTargetFundingIds() {
        return fundingJpaRepository.findByProjectIdIsNotNullAndProjectPublicIdIsNull().stream()
                .map(FundingJpaEntity::getId)
                .toList();
    }

    /** @return 이번 호출로 실제 채워졌으면 true, 이미 처리됐거나(재실행) publicId를 못 찾았으면 false */
    @Transactional
    public boolean backfillOne(Long fundingId) {
        FundingJpaEntity entity = fundingJpaRepository.findById(fundingId).orElse(null);
        if (entity == null || entity.getProjectId() == null || entity.getProjectPublicId() != null) {
            return false;
        }
        UUID publicId = projectOwnershipClient.findPublicId(entity.getProjectId()).orElse(null);
        if (publicId == null) {
            return false;
        }
        fundingJpaRepository.updateProjectPublicId(fundingId, publicId);
        return true;
    }
}

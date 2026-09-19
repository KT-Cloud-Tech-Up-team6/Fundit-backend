package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 내부 전용 조회 — fulfillment-service/order-service가 호출하는 최소 필드 조회. */
@Service
@RequiredArgsConstructor
public class ProjectInternalQueryService {

    private final ProjectRepository projectRepository;
    private final ProjectJpaRepository projectJpaRepository;
    private final SellerProfileClient sellerProfileClient;

    @Transactional(readOnly = true)
    public ProjectSnapshot getSnapshot(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return new ProjectSnapshot(project.getSellerId(), project.getPublicId());
    }

    /**
     * order-service 주문 목록(V03)용 배치 요약 조회 — 도메인 재구성 없이 프로젝션으로 직접 조회한다
     * (persistence-convention.md §3). 존재하지 않거나 삭제된 projectId는 결과에서 조용히 빠진다.
     */
    @Transactional(readOnly = true)
    public List<ProjectSummarySnapshot> getSummaries(List<UUID> publicIds) {
        if (publicIds.isEmpty()) {
            return List.of();
        }
        return projectJpaRepository.findSummariesByPublicIdIn(publicIds).stream()
                .map(p -> new ProjectSummarySnapshot(p.getPublicId(), p.getTitle(), p.getThumbnailUrl(),
                        sellerProfileClient.getDisplayName(p.getSellerId()).orElse(null)))
                .toList();
    }

    public record ProjectSnapshot(UUID sellerId, UUID publicId) {
    }

    public record ProjectSummarySnapshot(UUID publicId, String title, String thumbnailUrl, String sellerDisplayName) {
    }
}

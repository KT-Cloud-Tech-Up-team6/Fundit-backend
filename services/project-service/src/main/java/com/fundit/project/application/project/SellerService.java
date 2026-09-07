package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaEntity;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 판매자 정보/이력 조회(PROJECT-021). businessType은 member-service 소관이 아니라(member-service
 * CLAUDE.md 참고) 이 판매자가 등록한 프로젝트의 businessType 중 가장 최근 값을 사용한다 — 사업자
 * 유형은 프로젝트 단위 입력값이라 회원 전체를 대표하는 값이 별도로 없다[가정].
 */
@Service
@RequiredArgsConstructor
public class SellerService {

    private static final Set<String> PUBLIC_STATUSES = Set.of(
            ProjectStatus.ONGOING.name(), ProjectStatus.SUCCEEDED.name(), ProjectStatus.FAILED.name());

    private final ProjectJpaRepository projectJpaRepository;

    @Transactional(readOnly = true)
    public SellerProfileView getProfile(UUID sellerId) {
        List<ProjectJpaEntity> projects = projectJpaRepository.findBySellerIdAndDeletedAtIsNullOrderByCreatedAtDesc(sellerId);
        if (projects.isEmpty()) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }

        String businessType = projects.stream()
                .map(ProjectJpaEntity::getBusinessType)
                .filter(bt -> bt != null)
                .findFirst()
                .orElse(null);

        List<PastProjectView> pastProjects = projects.stream()
                .filter(p -> PUBLIC_STATUSES.contains(p.getStatus()))
                .map(p -> new PastProjectView(p.getPublicId(), p.getTitle(), p.getStatus()))
                .toList();

        return new SellerProfileView(sellerId, businessType, pastProjects);
    }

    public record PastProjectView(UUID projectId, String title, String status) {
    }

    public record SellerProfileView(UUID sellerId, String businessType, List<PastProjectView> pastProjects) {
    }
}

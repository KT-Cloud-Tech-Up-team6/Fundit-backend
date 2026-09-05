package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.BusinessType;
import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.category.CategoryJpaRepository;
import com.fundit.project.infrastructure.persistence.privacyconsent.ProjectPrivacyConsentJpaEntity;
import com.fundit.project.infrastructure.persistence.privacyconsent.ProjectPrivacyConsentJpaRepository;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import com.fundit.project.infrastructure.persistence.project.query.ProjectListProjection;
import com.fundit.project.infrastructure.persistence.reviewrequest.ProjectReviewRequestJpaEntity;
import com.fundit.project.infrastructure.persistence.reviewrequest.ProjectReviewRequestJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 프로젝트 생성/관리 슬라이스(project-service CLAUDE.md MVP 범위) — 판매자 관점의
 * 목록/생성/삭제/기본정보/소개/개인정보동의/심사제출을 다룬다. 심사 승인·반려(관리자)는
 * {@link ProjectReviewService} 참고.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    // 목록 조회는 persistence-convention.md §3(조회 전용 프로젝션) 예외에 따라
    // 도메인 재구성 없이 JpaRepository를 직접 사용한다.
    private final ProjectJpaRepository projectJpaRepository;
    private final CategoryJpaRepository categoryJpaRepository;
    private final ProjectPrivacyConsentJpaRepository privacyConsentJpaRepository;
    private final ProjectReviewRequestJpaRepository reviewRequestJpaRepository;
    private final RewardJpaRepository rewardJpaRepository;

    @Transactional(readOnly = true)
    public Page<ProjectListProjection> list(UUID sellerId, ProjectStatus status, Pageable pageable) {
        return projectJpaRepository.findList(sellerId, status == null ? null : status.name(), pageable);
    }

    @Transactional
    public Project create(UUID sellerId) {
        Instant now = Instant.now();
        Project project = Project.builder()
                .publicId(UuidCreator.getTimeOrderedEpoch())
                .sellerId(sellerId)
                .status(ProjectStatus.DRAFT)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return projectRepository.save(project);
    }

    @Transactional
    public void delete(UUID sellerId, UUID publicId) {
        Project project = loadOwned(sellerId, publicId);
        project.delete();
        projectRepository.save(project);
    }

    @Transactional
    public Project updateBasicInfo(UUID sellerId, UUID publicId, UpdateBasicInfoCommand command) {
        Project project = loadOwned(sellerId, publicId);

        if (command.categoryMajor() != null && command.categoryMinor() != null
                && !categoryJpaRepository.existsByCategoryMajorAndCategoryMinor(command.categoryMajor(), command.categoryMinor())) {
            throw new BusinessException(ProjectErrorCode.INVALID_CATEGORY);
        }

        BusinessType businessType = command.businessType() == null ? null : BusinessType.valueOf(command.businessType());
        project.updateBasicInfo(businessType, command.categoryMajor(), command.categoryMinor(),
                command.title(), command.goalAmount());
        return projectRepository.save(project);
    }

    @Transactional
    public Project updateStory(UUID sellerId, UUID publicId, UpdateStoryCommand command) {
        Project project = loadOwned(sellerId, publicId);
        project.updateStory(command.title(), command.coverImageUrl(), command.introContent());
        return projectRepository.save(project);
    }

    @Transactional
    public Instant consentPrivacy(UUID sellerId, UUID publicId, boolean agreed) {
        Project project = loadOwned(sellerId, publicId);
        if (!agreed) {
            throw new BusinessException(ProjectErrorCode.PRIVACY_CONSENT_REQUIRED);
        }
        Instant consentedAt = Instant.now();
        privacyConsentJpaRepository.save(ProjectPrivacyConsentJpaEntity.builder()
                .projectId(project.getId())
                .agreed(true)
                .consentedAt(consentedAt)
                .build());
        return consentedAt;
    }

    @Transactional
    public Project submit(UUID sellerId, UUID publicId) {
        Project project = loadOwned(sellerId, publicId);

        List<String> missing = missingRequiredItems(project);
        if (!missing.isEmpty()) {
            throw new BusinessException(ProjectErrorCode.PROJECT_NOT_SUBMITTABLE,
                    "필수 작성 항목이 완료되지 않았습니다: " + String.join(", ", missing));
        }

        project.submit();
        Project saved = projectRepository.save(project);

        reviewRequestJpaRepository.save(ProjectReviewRequestJpaEntity.builder()
                .projectId(saved.getId())
                .status(ProjectReviewRequestJpaEntity.STATUS_SUBMITTED)
                .submittedAt(Instant.now())
                .build());
        return saved;
    }

    private List<String> missingRequiredItems(Project project) {
        List<String> missing = new ArrayList<>();
        if (!project.hasCompletedBasicInfo()) missing.add("basicInfo");
        if (!project.hasStory()) missing.add("story");
        if (!rewardJpaRepository.existsByProjectIdAndDeletedAtIsNull(project.getId())) missing.add("rewards");
        if (!privacyConsentJpaRepository.existsByProjectIdAndAgreedTrue(project.getId())) missing.add("privacyConsent");
        return missing;
    }

    private Project loadOwned(UUID sellerId, UUID publicId) {
        Project project = projectRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (!project.isOwnedBy(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return project;
    }

    public record UpdateBasicInfoCommand(
            String businessType, String categoryMajor, String categoryMinor, String title, Long goalAmount) {
    }

    public record UpdateStoryCommand(String title, String coverImageUrl, List<IntroContentBlock> introContent) {
    }
}

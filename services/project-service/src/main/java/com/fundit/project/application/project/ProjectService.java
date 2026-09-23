package com.fundit.project.application.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.application.media.MediaCategory;
import com.fundit.project.application.media.MediaUrlValidator;
import com.fundit.project.application.project.ProjectIndexEventPublisher.ProjectIndexedEvent;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.domain.project.BusinessType;
import com.fundit.project.domain.project.IntroContentBlock;
import com.fundit.project.domain.project.IntroContentType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectRepository;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.category.CategoryJpaRepository;
import com.fundit.project.infrastructure.persistence.privacyconsent.ProjectPrivacyConsentJpaEntity;
import com.fundit.project.infrastructure.persistence.privacyconsent.ProjectPrivacyConsentJpaRepository;
import com.fundit.project.infrastructure.content.RichTextSanitizer;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import com.fundit.project.infrastructure.persistence.project.query.ProjectListProjection;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 프로젝트 생성/관리 슬라이스(project-service CLAUDE.md MVP 범위) — 판매자 관점의
 * 목록/생성/삭제/기본정보/소개/개인정보동의/공개(발행)를 다룬다. 관리자 심사 단계는
 * 폐지됐다 — 필수 항목이 모두 채워지면 {@link #submit}에서 바로 공개(ONGOING)로 전환한다.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    // [가정] PRD/API 명세서 어디에도 펀딩 기간(모금기간) 기본값이 명시돼 있지 않아 30일로 가정한다.
    // 기획에서 값이 확정되면 이 상수만 바꾸면 된다.
    private static final Duration DEFAULT_FUNDING_PERIOD = Duration.ofDays(30);

    private final ProjectRepository projectRepository;
    // 목록 조회는 persistence-convention.md §3(조회 전용 프로젝션) 예외에 따라
    // 도메인 재구성 없이 JpaRepository를 직접 사용한다.
    private final ProjectJpaRepository projectJpaRepository;
    private final CategoryJpaRepository categoryJpaRepository;
    private final ProjectPrivacyConsentJpaRepository privacyConsentJpaRepository;
    private final RewardJpaRepository rewardJpaRepository;
    private final MediaUrlValidator mediaUrlValidator;
    private final ProjectIndexEventPublisher projectIndexEventPublisher;
    private final SellerProfileClient sellerProfileClient;
    private final RichTextSanitizer richTextSanitizer;

    /** statuses가 비어있으면 전체 상태를 대상으로 한다. */
    @Transactional(readOnly = true)
    public Page<ProjectListProjection> list(UUID sellerId, List<ProjectStatus> statuses, String q, Pageable pageable) {
        List<String> statusNames = (statuses == null || statuses.isEmpty())
                ? Arrays.stream(ProjectStatus.values()).map(Enum::name).toList()
                : statuses.stream().map(Enum::name).toList();
        String keyword = (q == null || q.isBlank()) ? null : q.trim();
        return keyword == null
                ? projectJpaRepository.findList(sellerId, statusNames, pageable)
                : projectJpaRepository.findListByTitle(sellerId, statusNames, keyword, pageable);
    }

    /** 상태 그룹별(진행중/준비중/완료) 프로젝트 개수. */
    @Transactional(readOnly = true)
    public ProjectStatusCounts countByStatusGroup(UUID sellerId) {
        long draft = 0, ongoing = 0, completed = 0;
        for (var row : projectJpaRepository.countBySellerIdGroupByStatus(sellerId)) {
            ProjectStatus status = ProjectStatus.valueOf(row.getStatus());
            switch (status) {
                case DRAFT -> draft += row.getCount();
                case ONGOING -> ongoing += row.getCount();
                case SUCCEEDED, FAILED -> completed += row.getCount();
            }
        }
        return new ProjectStatusCounts(ongoing, draft, completed);
    }

    public record ProjectStatusCounts(long ongoing, long draft, long completed) {
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
        Project saved = projectRepository.save(project);
        publishIndexUpdateIfPublic(saved);
        return saved;
    }

    @Transactional
    public Project updateStory(UUID sellerId, UUID publicId, UpdateStoryCommand command) {
        Project project = loadOwned(sellerId, publicId);
        if (command.coverImageUrl() != null) {
            mediaUrlValidator.validate(publicId, command.coverImageUrl(), MediaCategory.IMAGE);
        }
        List<IntroContentBlock> introContent = command.introContent() == null ? null
                : sanitizeIntroContent(publicId, command.introContent());
        project.updateStory(command.title(), command.coverImageUrl(), introContent);
        Project saved = projectRepository.save(project);
        publishIndexUpdateIfPublic(saved);
        return saved;
    }

    /**
     * IMAGE는 S3 업로드 여부를 검증하고(ApiSpec #8), TEXT는 굵게/색상/정렬 서식을 보존하되
     * XSS를 막기 위해 화이트리스트로 정제한다(security.md S2). VIDEO_URL은 유튜브 등 외부 링크라
     * 둘 다 대상이 아니다.
     */
    private List<IntroContentBlock> sanitizeIntroContent(UUID publicId, List<IntroContentBlock> blocks) {
        List<IntroContentBlock> sanitized = new ArrayList<>();
        for (IntroContentBlock block : blocks) {
            switch (block.type()) {
                case IMAGE -> {
                    mediaUrlValidator.validate(publicId, block.value(), MediaCategory.IMAGE);
                    sanitized.add(block);
                }
                case TEXT -> sanitized.add(new IntroContentBlock(IntroContentType.TEXT,
                        richTextSanitizer.sanitize(block.value())));
                case VIDEO_URL -> sanitized.add(block);
            }
        }
        return sanitized;
    }

    /**
     * SEARCH-011. 공개 전(DRAFT) 수정은 애초에 색인에 없는 프로젝트를 갱신하는
     * 셈이라 발행하지 않는다 — Project.isPublic()과 동일 기준(project-service CLAUDE.md
     * "미공개 프로젝트 존재 여부 비노출" 원칙).
     */
    private void publishIndexUpdateIfPublic(Project project) {
        if (!project.isPublic()) {
            return;
        }
        String sellerDisplayName = sellerProfileClient.getDisplayName(project.getSellerId()).orElse(null);
        projectIndexEventPublisher.publishProjectUpdated(new ProjectIndexedEvent(
                project.getId(), project.getPublicId(), project.getSellerId(), sellerDisplayName,
                project.getTitle(), project.getCoverImageUrl(), project.getCategoryMajor(), project.getCategoryMinor(),
                project.getGoalAmount(), project.getFundingStartAt(), project.getFundingDeadline(), project.getCreatedAt()));
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

    /** 필수 작성 항목이 모두 채워지면 관리자 승인 없이 바로 공개(ONGOING)로 전환한다. */
    @Transactional
    public Project submit(UUID sellerId, UUID publicId) {
        Project project = loadOwned(sellerId, publicId);

        List<String> missing = missingRequiredItems(project);
        if (!missing.isEmpty()) {
            throw new BusinessException(ProjectErrorCode.PROJECT_NOT_SUBMITTABLE,
                    "필수 작성 항목이 완료되지 않았습니다: " + String.join(", ", missing));
        }

        Instant now = Instant.now();
        project.publish(now, now.plus(DEFAULT_FUNDING_PERIOD));
        Project saved = projectRepository.save(project);

        // SEARCH-011. 색인이 처음 생기는 시점 — DRAFT는 비공개라 그전엔 검색 대상이 아니다.
        String sellerDisplayName = sellerProfileClient.getDisplayName(saved.getSellerId()).orElse(null);
        projectIndexEventPublisher.publishProjectApproved(new ProjectIndexedEvent(
                saved.getId(), saved.getPublicId(), saved.getSellerId(), sellerDisplayName,
                saved.getTitle(), saved.getCoverImageUrl(), saved.getCategoryMajor(), saved.getCategoryMinor(),
                saved.getGoalAmount(), saved.getFundingStartAt(), saved.getFundingDeadline(), saved.getCreatedAt()));
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

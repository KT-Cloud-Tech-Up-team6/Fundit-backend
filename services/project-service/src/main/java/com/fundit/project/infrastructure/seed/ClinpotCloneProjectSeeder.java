package com.fundit.project.infrastructure.seed;

import com.fundit.project.application.project.ProjectIndexEventPublisher;
import com.fundit.project.application.project.ProjectIndexEventPublisher.ProjectIndexedEvent;
import com.fundit.project.application.project.SellerProfileClient;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaEntity;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaRepository;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaEntity;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionGroupJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionGroupJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionValueJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardOptionValueJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

/**
 * dev 전용 시연 데이터(#234 3번) — 진행 중인 클린팟 프로젝트를 그대로 복제해 펀딩이 성립된(완료) 프로젝트로 넣는다.
 * PM이 같은 프로젝트로 "완료 프로젝트 → 펀딩 관리 → 제작·배송"까지 시연하려고 요청했다.
 *
 * <p>값을 코드에 박지 않고 원본 행(같은 서비스 DB)을 읽어 복제한다 — 제목·커버·스토리·리워드가 원본과 같아야 하고,
 * dev DB를 초기화해 원본이 다시 만들어져도 그대로 따라간다. 원본이 아직 없으면 건너뛴다(다음 기동에 다시 시도).
 *
 * <p>리워드는 무제한으로 복제한다 — 한정 리워드는 order 재고 행이 있어야 하는데 리워드 생성 이벤트를 내지 않는다
 * ({@link DemoFulfillmentProjectSeeder}와 같은 이유). 완료 프로젝트라 재고가 화면에 의미가 없다.
 * 스냅샷은 0으로 만든다 — 실제 수치는 order {@code ClinpotFundingSeeder}가 펀딩을 넣고 부르는 집계 이벤트가 채운다.
 *
 * <p>{@link #CLONE_PROJECT_ID}는 order·search·fulfillment 시연 시더와 같은 값이어야 한다(4곳 동일).
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class ClinpotCloneProjectSeeder implements ApplicationRunner {

    static final UUID SOURCE_PROJECT_ID = UUID.fromString("01a0fa1e-074a-7d87-a43b-4e0a947bfd13");
    static final UUID CLONE_PROJECT_ID = UUID.fromString("ad5565eb-ffb1-316a-bc4c-d4cc6e9b89ea");

    private final ProjectJpaRepository projectRepository;
    private final FundingStatusSnapshotJpaRepository snapshotRepository;
    private final RewardJpaRepository rewardRepository;
    private final RewardOptionGroupJpaRepository optionGroupRepository;
    private final RewardOptionValueJpaRepository optionValueRepository;
    private final SellerProfileClient sellerProfileClient;
    private final ProjectIndexEventPublisher indexEventPublisher;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed(Instant.now());
        } catch (RuntimeException e) {
            log.warn("클린팟 클론 시드 실패", e);
        }
    }

    /** @return 새로 만들었으면 true */
    boolean seed(Instant base) {
        if (projectRepository.existsByPublicId(CLONE_PROJECT_ID)) {
            return false;
        }
        Optional<ProjectJpaEntity> source = projectRepository.findByPublicIdAndDeletedAtIsNull(SOURCE_PROJECT_ID);
        if (source.isEmpty()) {
            log.warn("클린팟 원본이 없어 클론 시드를 건너뛴다 publicId={}", SOURCE_PROJECT_ID);
            return false;
        }
        // 회원 조회(외부 호출)는 트랜잭션 밖에서 끝낸다.
        String sellerDisplayName = sellerProfileClient.getDisplayName(source.get().getSellerId()).orElse(null);
        // 프로젝트·스냅샷·리워드·색인 이벤트를 한 트랜잭션으로 — 나뉘면 다음 기동에 "이미 있음"으로 건너뛰어 빠진 게 영영 안 채워진다.
        transactionTemplate.executeWithoutResult(status -> save(source.get(), sellerDisplayName, base));
        log.info("클린팟 클론 시드 완료 publicId={}", CLONE_PROJECT_ID);
        return true;
    }

    private void save(ProjectJpaEntity source, String sellerDisplayName, Instant base) {
        Instant deadline = base.minus(Duration.ofDays(15));
        ProjectJpaEntity clone = projectRepository.save(ProjectJpaEntity.builder()
                .publicId(CLONE_PROJECT_ID)
                .sellerId(source.getSellerId())
                .businessType(source.getBusinessType())
                .categoryMajor(source.getCategoryMajor())
                .categoryMinor(source.getCategoryMinor())
                .title(source.getTitle())
                .goalAmount(source.getGoalAmount())
                .fundingStartAt(base.minus(Duration.ofDays(45)))
                .fundingDeadline(deadline)
                .coverImageUrl(source.getCoverImageUrl())
                .introContent(source.getIntroContent() == null ? null : new ArrayList<>(source.getIntroContent()))
                .status(ProjectStatus.SUCCEEDED.name())
                // 마감 감시(FundingDeadlineWatcher, ONGOING만 본다)에 다시 걸리지 않게 한다.
                .deadlineNotifiedAt(deadline)
                .build());
        snapshotRepository.save(FundingStatusSnapshotJpaEntity.builder()
                .projectId(clone.getId())
                .currentAmount(0L)
                .achievementRate(0)
                .participantCount(0)
                .lastSyncedAt(base)
                .build());
        for (RewardJpaEntity reward : rewardRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(source.getId())) {
            copyReward(reward, clone.getId());
        }
        indexEventPublisher.publishProjectApproved(new ProjectIndexedEvent(
                clone.getId(), clone.getPublicId(), clone.getSellerId(), sellerDisplayName,
                clone.getTitle(), clone.getCoverImageUrl(), clone.getCategoryMajor(), clone.getCategoryMinor(),
                clone.getGoalAmount(), clone.getFundingStartAt(), clone.getFundingDeadline(), clone.getCreatedAt()));
    }

    private void copyReward(RewardJpaEntity source, Long cloneProjectId) {
        RewardJpaEntity reward = rewardRepository.save(RewardJpaEntity.builder()
                .projectId(cloneProjectId)
                .name(source.getName())
                .description(source.getDescription())
                .imageUrl(source.getImageUrl())
                .price(source.getPrice())
                .isLimited(false)
                .isEarlyBird(source.getIsEarlyBird())
                .earlyBirdDiscountType(source.getEarlyBirdDiscountType())
                .earlyBirdDiscountValue(source.getEarlyBirdDiscountValue())
                .hasOption(source.getHasOption())
                .sortOrder(source.getSortOrder())
                .simpleRefundDisabled(source.getSimpleRefundDisabled())
                .shippingFee(source.getShippingFee())
                .estimatedDeliveryDays(source.getEstimatedDeliveryDays())
                .build());
        for (RewardOptionGroupJpaEntity group : optionGroupRepository.findByRewardIdAndDeletedAtIsNullOrderBySortOrderAsc(source.getId())) {
            RewardOptionGroupJpaEntity groupClone = optionGroupRepository.save(RewardOptionGroupJpaEntity.builder()
                    .rewardId(reward.getId())
                    .name(group.getName())
                    .sortOrder(group.getSortOrder())
                    .build());
            for (RewardOptionValueJpaEntity value : optionValueRepository.findByOptionGroupIdOrderBySortOrderAsc(group.getId())) {
                if (value.getDeletedAt() != null) {
                    continue;
                }
                optionValueRepository.save(RewardOptionValueJpaEntity.builder()
                        .optionGroupId(groupClone.getId())
                        .value(value.getValue())
                        .sortOrder(value.getSortOrder())
                        .build());
            }
        }
    }
}

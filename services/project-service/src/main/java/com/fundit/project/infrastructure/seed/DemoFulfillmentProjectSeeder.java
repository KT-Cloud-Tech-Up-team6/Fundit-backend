package com.fundit.project.infrastructure.seed;

import com.fundit.project.application.project.ProjectIndexEventPublisher;
import com.fundit.project.application.project.ProjectIndexEventPublisher.ProjectIndexedEvent;
import com.fundit.project.domain.project.ProjectStatus;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaEntity;
import com.fundit.project.infrastructure.persistence.fundingstatus.FundingStatusSnapshotJpaRepository;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaEntity;
import com.fundit.project.infrastructure.persistence.project.ProjectJpaRepository;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaEntity;
import com.fundit.project.infrastructure.persistence.reward.RewardJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * dev 전용 시연 데이터(#202) — 펀딩이 성립된 프로젝트 1건을 넣는다. 시연 계정으로 "펀딩 내역 → 제작·배송 현황"과
 * 판매자 관리 화면을 보여 주려면 성립 상태가 필요한데, 코드에 프로젝트를 {@code SUCCEEDED}로 만드는 경로가 없다.
 *
 * <p>판매자는 dev에 이미 있는 QA 판매자 계정({@code demo.seller-member-id})이다 — 로그인해서 판매자 화면을
 * 시연해야 해서 목업 판매자(로그인 불가)를 쓰지 않는다. 값이 비면 건너뛴다.
 *
 * <p>{@code deadlineNotifiedAt}을 채워 마감 감시({@code FundingDeadlineWatcher}, ONGOING만 본다)에 걸리지 않게 한다.
 * 리워드는 무제한이라 order 재고 행이 필요 없어 리워드 생성 이벤트를 내지 않는다.
 *
 * <p>시연 프로젝트 공개 ID {@link #DEMO_PROJECT_ID}는 search·order·fulfillment 시연 시더와 같은 값이어야 한다(4곳 동일).
 * 이미 있으면 건너뛴다 — 날짜는 최초 생성 시점 기준이지만 마감이 계속 과거라 시간이 지나도 문제없다.
 */
@Slf4j
@Component
@Profile("dev")
public class DemoFulfillmentProjectSeeder implements ApplicationRunner {

    static final UUID DEMO_PROJECT_ID = UUID.fromString("f8c82570-d800-3d07-9dbf-82b091690ce5");
    static final String TITLE = "[접어서 간편하게] 캔버스 수납 바스켓";
    /** QA 판매자 계정 닉네임({@code QaTestAccountSeeder}) — 색인 이벤트의 판매자 표시명. */
    static final String SELLER_NICKNAME = "QA판매자";

    private final ProjectJpaRepository projectRepository;
    private final FundingStatusSnapshotJpaRepository snapshotRepository;
    private final RewardJpaRepository rewardRepository;
    private final ProjectIndexEventPublisher indexEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final String sellerMemberId;

    public DemoFulfillmentProjectSeeder(ProjectJpaRepository projectRepository,
                                        FundingStatusSnapshotJpaRepository snapshotRepository,
                                        RewardJpaRepository rewardRepository,
                                        ProjectIndexEventPublisher indexEventPublisher,
                                        TransactionTemplate transactionTemplate,
                                        @Value("${demo.seller-member-id:}") String sellerMemberId) {
        this.projectRepository = projectRepository;
        this.snapshotRepository = snapshotRepository;
        this.rewardRepository = rewardRepository;
        this.indexEventPublisher = indexEventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.sellerMemberId = sellerMemberId;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed(Instant.now());
        } catch (RuntimeException e) {
            log.warn("시연 프로젝트 시드 실패", e);
        }
    }

    /** @return 새로 만들었으면 true */
    boolean seed(Instant base) {
        if (sellerMemberId.isBlank()) {
            log.warn("demo.seller-member-id가 비어 시연 프로젝트 시드를 건너뛴다");
            return false;
        }
        if (projectRepository.existsByPublicId(DEMO_PROJECT_ID)) {
            return false;
        }
        // 프로젝트·스냅샷·리워드·색인 이벤트를 한 트랜잭션으로 — 나뉘면 다음 기동에 "이미 있음"으로 건너뛰어 빠진 게 영영 안 채워진다.
        transactionTemplate.executeWithoutResult(status -> save(UUID.fromString(sellerMemberId), base));
        log.info("시연 프로젝트 시드 완료 publicId={}", DEMO_PROJECT_ID);
        return true;
    }

    private void save(UUID sellerId, Instant base) {
        Instant deadline = daysFrom(base, -15);
        ProjectJpaEntity saved = projectRepository.save(ProjectJpaEntity.builder()
                .publicId(DEMO_PROJECT_ID)
                .sellerId(sellerId)
                .categoryMajor("홈·리빙")
                .categoryMinor("인테리어")
                .title(TITLE)
                .goalAmount(3_000_000L)
                .fundingStartAt(daysFrom(base, -45))
                .fundingDeadline(deadline)
                .coverImageUrl("https://infrastudy.store/media/mock/demo-cover.png")
                .status(ProjectStatus.SUCCEEDED.name())
                .deadlineNotifiedAt(deadline)
                .build());
        snapshotRepository.save(FundingStatusSnapshotJpaEntity.builder()
                .projectId(saved.getId())
                .currentAmount(4_500_000L)
                .achievementRate(150)
                .participantCount(150)
                .lastSyncedAt(base)
                .build());
        rewardRepository.save(RewardJpaEntity.builder()
                .projectId(saved.getId())
                .name("[얼리버드] 캔버스 수납 바스켓 1개")
                .description("접이식 캔버스 수납 바스켓 1개 / 아이보리 / 약 35 × 25 × 25cm / 손잡이 포함")
                .price(30_000L)
                .isLimited(false)
                .isEarlyBird(true)
                .shippingFee(2_500L)
                .build());
        indexEventPublisher.publishProjectApproved(new ProjectIndexedEvent(
                saved.getId(), saved.getPublicId(), saved.getSellerId(), SELLER_NICKNAME,
                saved.getTitle(), saved.getCoverImageUrl(), saved.getCategoryMajor(), saved.getCategoryMinor(),
                saved.getGoalAmount(), saved.getFundingStartAt(), saved.getFundingDeadline(), saved.getCreatedAt()));
    }

    private static Instant daysFrom(Instant base, int days) {
        return base.plus(Duration.ofDays(days));
    }
}

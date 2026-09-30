package com.fundit.order.infrastructure.seed;

import com.fundit.order.application.catalog.RewardCatalogClient;
import com.fundit.order.application.catalog.RewardCatalogClient.RewardSnapshot;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaEntity;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemJpaEntity;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.ShippingAddressJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * dev 전용 시연 데이터(#202) — QA 구매자 계정({@code demo.buyer-member-id})의 성립된 펀딩 1건을 넣는다.
 * 펀딩 내역에서 "제작·배송 현황"으로 들어가려면 {@code GOAL_ACHIEVED} 주문이 있어야 하고, 판매자 주문 목록도
 * 이 행({@code project_public_id}·{@code GOAL_ACHIEVED})으로 보인다.
 *
 * <p>라인 항목의 {@code reward_id}는 project 내부 리워드 id라 여기서 미리 알 수 없다 — 기존 리워드 조회
 * ({@link RewardCatalogClient}, 타임아웃 설정됨)로 받아 온다. project 시연 시더가 아직 안 돌았거나 project가 안 떠
 * 있으면 다음 주기에 다시 시도하고, 한 번 넣은 뒤로는 아무것도 하지 않는다.
 *
 * <p>이벤트는 내지 않는다 — {@code funding.succeeded.v1}을 내면 fulfillment·payment까지 반응한다. 그래서 정산 일정은
 * 생기지 않는다(시연 범위 밖). 목표 판정({@code FundingGoalJudgmentService})은 PENDING·FUNDING_IN_PROGRESS만 봐서
 * 이 행을 건드리지 않는다.
 *
 * <p>{@link #DEMO_PROJECT_ID}는 project·search·fulfillment 시연 시더와 같은 값이다(4곳 동일).
 */
@Slf4j
@Component
@Profile("dev")
public class DemoFundingSeeder {

    static final UUID DEMO_PROJECT_ID = UUID.fromString("f8c82570-d800-3d07-9dbf-82b091690ce5");
    static final UUID DEMO_FUNDING_ID = UUID.fromString("8f57fd0a-4b52-3152-a59d-aa4b30a8a67d");
    static final String PROJECT_TITLE = "[접어서 간편하게] 캔버스 수납 바스켓";
    static final long SHIPPING_FEE = 2_500L;

    private final FundingJpaRepository fundingRepository;
    private final FundingLineItemJpaRepository lineItemRepository;
    private final RewardCatalogClient rewardCatalogClient;
    private final TransactionTemplate transactionTemplate;
    private final String buyerMemberId;
    private final Clock clock;
    private volatile boolean done;

    public DemoFundingSeeder(FundingJpaRepository fundingRepository,
                             FundingLineItemJpaRepository lineItemRepository,
                             RewardCatalogClient rewardCatalogClient,
                             TransactionTemplate transactionTemplate,
                             @Value("${demo.buyer-member-id:}") String buyerMemberId) {
        this(fundingRepository, lineItemRepository, rewardCatalogClient, transactionTemplate, buyerMemberId,
                Clock.systemUTC());
    }

    DemoFundingSeeder(FundingJpaRepository fundingRepository, FundingLineItemJpaRepository lineItemRepository,
                      RewardCatalogClient rewardCatalogClient, TransactionTemplate transactionTemplate,
                      String buyerMemberId, Clock clock) {
        this.fundingRepository = fundingRepository;
        this.lineItemRepository = lineItemRepository;
        this.rewardCatalogClient = rewardCatalogClient;
        this.transactionTemplate = transactionTemplate;
        this.buyerMemberId = buyerMemberId;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void seed() {
        if (done) {
            return;
        }
        if (buyerMemberId.isBlank()) {
            log.warn("demo.buyer-member-id가 비어 시연 펀딩 시드를 건너뛴다");
            done = true;
            return;
        }
        if (fundingRepository.findByPublicId(DEMO_FUNDING_ID).isPresent()) {
            done = true;
            return;
        }
        List<RewardSnapshot> rewards;
        try {
            rewards = rewardCatalogClient.getRewards(DEMO_PROJECT_ID);
        } catch (RuntimeException e) {
            log.info("시연 프로젝트 리워드를 아직 못 받아 다음 주기에 다시 시도한다: {}", e.getMessage());
            return;
        }
        if (rewards.isEmpty()) {
            return;
        }
        Instant base = clock.instant();
        transactionTemplate.executeWithoutResult(status -> save(UUID.fromString(buyerMemberId), rewards.getFirst(), base));
        done = true;
        log.info("시연 펀딩 시드 완료 publicId={}", DEMO_FUNDING_ID);
    }

    private void save(UUID buyerId, RewardSnapshot reward, Instant base) {
        Instant paidAt = base.minus(Duration.ofDays(30));
        FundingJpaEntity funding = fundingRepository.save(FundingJpaEntity.builder()
                .publicId(DEMO_FUNDING_ID)
                .memberId(buyerId)
                .projectPublicId(DEMO_PROJECT_ID)
                .projectTitle(PROJECT_TITLE)
                .status(FundingStatus.GOAL_ACHIEVED.name())
                .shippingAddress(new ShippingAddressJson("QA서포터", "010-1234-5678", "06236",
                        "서울시 강남구 테헤란로 1", "101호"))
                .shippingFee(SHIPPING_FEE)
                .paymentExpiresAt(paidAt.plus(Duration.ofMinutes(30)))
                .paidAt(paidAt)
                .decidedAt(base.minus(Duration.ofDays(15)))
                .createdAt(paidAt)
                .build());
        lineItemRepository.save(FundingLineItemJpaEntity.builder()
                .fundingId(funding.getId())
                .rewardId(reward.rewardId())
                .rewardName(reward.name())
                .quantity(1)
                .unitPrice(reward.price())
                .build());
    }
}

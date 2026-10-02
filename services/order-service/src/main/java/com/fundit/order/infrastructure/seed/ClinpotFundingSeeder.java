package com.fundit.order.infrastructure.seed;

import com.fundit.order.application.catalog.RewardCatalogClient;
import com.fundit.order.application.catalog.RewardCatalogClient.OptionGroupSnapshot;
import com.fundit.order.application.catalog.RewardCatalogClient.RewardSnapshot;
import com.fundit.order.application.funding.FundingRewardStatsBatchService;
import com.fundit.order.domain.funding.FundingStatus;
import com.fundit.order.domain.inventory.InventoryRepository;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaEntity;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemJpaEntity;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemOptionJpaEntity;
import com.fundit.order.infrastructure.persistence.funding.FundingLineItemOptionJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.ShippingAddressJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * dev 전용 시연 데이터(#234) — 소비자 시연용 클린팟 프로젝트의 진행 중 펀딩 30건을 넣는다.
 * 0% / 0원 / 0명으로는 시연이 안 돼서 PM이 요청한 건이다.
 *
 * <p>구매자 30명은 {@code UUID.nameUUIDFromBytes}로 이름에서 만든 고정 UUID다 — {@code fundings.member_id}는
 * FK가 아니라 member-service에 행을 만들지 않아도 되고, 후원자 수가 {@code count(DISTINCT member_id)}라
 * 서로 달라야 한다. 판매자 발송 목록({@code SellerOrderResponse})은 구매자 식별자를 내려주지 않아
 * 이름은 배송지에만 있으면 된다.
 *
 * <p>리워드 id·가격·옵션은 {@link RewardCatalogClient}로 받아 온다 — project 내부 PK라 코드에 박으면
 * dev DB를 초기화할 때 조용히 깨진다. 얼리버드 할인은 클라이언트가 이미 접어서 준다
 * ({@code ProjectServiceRewardCatalogClient}, 정가가 아니라 할인가가 {@code unitPrice}로 들어간다).
 *
 * <p><b>한정 리워드는 재고도 같이 차감한다</b>({@code decreaseStock}) — 실주문 경로({@code OrderCreateService})와
 * 같은 메서드다. 안 줄이면 "30개 팔렸는데 재고 그대로"가 상세 화면에 보인다. 다만 되돌리는 경로는 없으니
 * (취소 API를 타지 않는다) 시연 뒤 목업을 지우면 재고는 손으로 원복해야 한다.
 *
 * <p>이벤트는 내지 않는다 — {@code funding.succeeded.v1}을 내면 fulfillment·payment까지 반응한다
 * ({@code DemoFundingSeeder}와 같은 이유). 대신 {@link FundingRewardStatsBatchService#recomputeOne}을
 * 저장 뒤에 불러 판매자 펀딩 관리와 search 카드에 수치를 반영한다(#230 — 집계가 네이티브 쿼리라 순서가 중요).
 *
 * <p>리워드 조회가 project-service 기동에 달려 있어 {@code ApplicationRunner}가 아니라 스케줄로 재시도한다.
 */
@Slf4j
@Component
@Profile("dev")
public class ClinpotFundingSeeder {

    static final UUID PROJECT_ID = UUID.fromString("01a0fa1e-074a-7d87-a43b-4e0a947bfd13");
    static final String PROJECT_TITLE = "[신제품 최초 공개] 음식물 냄새를 한 번에, 클린팟 미니 음식물처리기";
    /** 얼리버드(한정) 건수 — 나머지는 두 번째 리워드로 채운다. */
    static final int EARLY_BIRD_COUNT = 10;
    static final int TOTAL_COUNT = 30;
    static final long SHIPPING_FEE = 3_000L;

    private final FundingJpaRepository fundingRepository;
    private final FundingLineItemJpaRepository lineItemRepository;
    private final FundingLineItemOptionJpaRepository optionRepository;
    private final RewardCatalogClient rewardCatalogClient;
    private final InventoryRepository inventoryRepository;
    private final FundingRewardStatsBatchService rewardStatsBatchService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private volatile boolean done;

    /** 생성자가 둘이라(테스트용 {@link Clock} 주입) Spring이 쓸 쪽을 지정한다 — 없으면 빈 생성이 실패한다. */
    @Autowired
    public ClinpotFundingSeeder(FundingJpaRepository fundingRepository,
                                FundingLineItemJpaRepository lineItemRepository,
                                FundingLineItemOptionJpaRepository optionRepository,
                                RewardCatalogClient rewardCatalogClient,
                                InventoryRepository inventoryRepository,
                                FundingRewardStatsBatchService rewardStatsBatchService,
                                TransactionTemplate transactionTemplate) {
        this(fundingRepository, lineItemRepository, optionRepository, rewardCatalogClient, inventoryRepository,
                rewardStatsBatchService, transactionTemplate, Clock.systemUTC());
    }

    ClinpotFundingSeeder(FundingJpaRepository fundingRepository, FundingLineItemJpaRepository lineItemRepository,
                         FundingLineItemOptionJpaRepository optionRepository, RewardCatalogClient rewardCatalogClient,
                         InventoryRepository inventoryRepository, FundingRewardStatsBatchService rewardStatsBatchService,
                         TransactionTemplate transactionTemplate, Clock clock) {
        this.fundingRepository = fundingRepository;
        this.lineItemRepository = lineItemRepository;
        this.optionRepository = optionRepository;
        this.rewardCatalogClient = rewardCatalogClient;
        this.inventoryRepository = inventoryRepository;
        this.rewardStatsBatchService = rewardStatsBatchService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void seed() {
        if (done) {
            return;
        }
        // 30건을 한 트랜잭션으로 넣으므로 첫 건만 보면 이미 넣었는지 알 수 있다.
        if (fundingRepository.findByPublicId(fundingId(0)).isPresent()) {
            done = true;
            return;
        }
        List<RewardSnapshot> rewards;
        try {
            rewards = rewardCatalogClient.getRewards(PROJECT_ID);
        } catch (RuntimeException e) {
            log.info("클린팟 리워드를 아직 못 받아 다음 주기에 다시 시도한다: {}", e.getMessage());
            return;
        }
        if (rewards.size() < 2) {
            log.warn("클린팟 리워드가 2개 미만이라 시드를 건너뛴다 — size={}", rewards.size());
            done = true;
            return;
        }
        transactionTemplate.executeWithoutResult(status -> save(rewards, clock.instant()));
        done = true;
    }

    private void save(List<RewardSnapshot> rewards, Instant base) {
        long amount = 0;
        for (int i = 0; i < TOTAL_COUNT; i++) {
            RewardSnapshot reward = i < EARLY_BIRD_COUNT ? rewards.get(0) : rewards.get(1);
            saveFunding(i, reward, base);
            amount += reward.price();
        }
        decreaseStock(rewards.get(0), EARLY_BIRD_COUNT);
        decreaseStock(rewards.get(1), TOTAL_COUNT - EARLY_BIRD_COUNT);
        // 집계가 네이티브 쿼리라 반드시 저장 뒤에 부른다 — 앞에 두면 0원으로 집계된다(#230).
        rewardStatsBatchService.recomputeOne(PROJECT_ID);
        log.info("클린팟 펀딩 시드 완료 — {}건, {}원", TOTAL_COUNT, amount);
    }

    private void saveFunding(int index, RewardSnapshot reward, Instant base) {
        // 참여 시점을 하루씩 흩어 둔다 — 30건이 같은 초에 몰리면 목록이 부자연스럽다.
        Instant paidAt = base.minus(Duration.ofDays(TOTAL_COUNT - index));
        FundingJpaEntity funding = fundingRepository.save(FundingJpaEntity.builder()
                .publicId(fundingId(index))
                .memberId(buyerId(index))
                .projectPublicId(PROJECT_ID)
                .projectTitle(PROJECT_TITLE)
                .status(FundingStatus.FUNDING_IN_PROGRESS.name())
                .shippingAddress(new ShippingAddressJson("클린팟서포터%02d".formatted(index + 1),
                        "010-0000-%04d".formatted(index + 1), "06236", "서울시 강남구 테헤란로 1",
                        "%d호".formatted(index + 101)))
                .shippingFee(SHIPPING_FEE)
                .paymentExpiresAt(paidAt.plus(Duration.ofMinutes(30)))
                .paidAt(paidAt)
                .createdAt(paidAt)
                .build());
        FundingLineItemJpaEntity lineItem = lineItemRepository.save(FundingLineItemJpaEntity.builder()
                .fundingId(funding.getId())
                .rewardId(reward.rewardId())
                .rewardName(reward.name())
                .quantity(1)
                .unitPrice(reward.price())
                .build());
        saveOptions(lineItem, reward, index);
    }

    /** 옵션 행이 있어야 리워드 현황이 옵션별로도 나온다(집계 쿼리의 두 번째 UNION 가지). 값은 번갈아 고른다. */
    private void saveOptions(FundingLineItemJpaEntity lineItem, RewardSnapshot reward, int index) {
        for (OptionGroupSnapshot group : reward.optionGroups()) {
            if (group.values().isEmpty()) {
                continue;
            }
            var value = group.values().get(index % group.values().size());
            optionRepository.save(FundingLineItemOptionJpaEntity.builder()
                    .fundingLineItemId(lineItem.getId())
                    .optionGroupId(group.groupId())
                    .optionGroupName(group.groupName())
                    .optionValueId(value.valueId())
                    .optionValue(value.value())
                    .build());
        }
    }

    /** 한정 리워드만 재고 행이 있다. 부족하면 false가 오는데, 시연 데이터라 경고만 남기고 진행한다. */
    private void decreaseStock(RewardSnapshot reward, int quantity) {
        if (!reward.isLimited() || quantity <= 0) {
            return;
        }
        if (!inventoryRepository.decreaseStock(reward.rewardId(), quantity)) {
            log.warn("클린팟 재고 차감 실패(재고 부족 또는 재고 행 없음) rewardId={} quantity={}",
                    reward.rewardId(), quantity);
        }
    }

    /** 이름에서 만든 고정 UUID — 재기동해도 같은 값이라 중복 생성되지 않는다. */
    static UUID fundingId(int index) {
        return nameUuid("clinpot-funding-" + index);
    }

    static UUID buyerId(int index) {
        return nameUuid("clinpot-buyer-" + index);
    }

    private static UUID nameUuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
}

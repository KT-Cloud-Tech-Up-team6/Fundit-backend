package com.fundit.fulfillment.infrastructure.seed;

import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaRepository;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaRepository;
import com.fundit.fulfillment.infrastructure.persistence.tracker.FulfillmentTrackerJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.tracker.FulfillmentTrackerJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * dev 전용 시연 데이터(#202) — 시연 프로젝트의 제작·배송 현황을 "생산 중"으로 넣는다.
 * 트래커는 원래 {@code funding.succeeded.v1}로만 생기는데 시연 데이터엔 그 이벤트가 없다.
 *
 * <p>"생산 중" = {@code current_stage=MANUFACTURING}(앞 단계 완료·뒤 단계 진행 전은 조회 시 계산). 단계마다 기록 1건
 * (조회는 단계별 최신 1건만 보여 준다), 일정 변경 1건.
 *
 * <p><b>기동할 때마다 날짜를 다시 계산한다.</b> 절대 날짜로 두면 시간이 지나 마지막 기록이 7일을 넘기면 미갱신 경고가,
 * 출고 계획 종료일이 지나면 "발송 지연"이 뜬다. 단 판매자가 시연 중 기록·단계를 바꿨으면(시드 모양이 아니면)
 * 그 데이터를 지우지 않도록 건드리지 않는다. 되돌리려면 dev에서 시연 트래커와 그 기록·일정 변경을 지우고 재기동한다.
 *
 * <p>{@link #DEMO_PROJECT_ID}는 project·search·order 시연 시더와 같은 값이다(4곳 동일).
 */
@Slf4j
@Component
@Profile("dev")
public class DemoFulfillmentSeeder implements ApplicationRunner {

    static final UUID DEMO_PROJECT_ID = UUID.fromString("f8c82570-d800-3d07-9dbf-82b091690ce5");
    static final String CURRENT_STAGE = "MANUFACTURING";
    private static final String PHOTO_BASE = "https://infrastudy.store/media/mock/";

    /** 단계별 기록 1건 — 계획일·기록일(updated_at)은 기준 시각 대비 일수. */
    record SeedDetail(String stage, int plannedStartDays, int plannedEndDays, String text, String photo, int updatedDays) {
    }

    static final List<SeedDetail> DETAILS = List.of(
            new SeedDetail("PRODUCTION_START", -14, -11,
                    "최종 샘플의 크기와 봉제 사양을 확정하고 원단 재단 준비를 마쳤습니다. 협력 작업장에 총 150개 제작을 발주했습니다.",
                    "demo-production-start-1.png", -11),
            new SeedDetail("MANUFACTURING", -10, 7,
                    "손잡이 자재가 입고되어 봉제 작업을 재개하고 있습니다. 총 150개 중 90개 제작을 마쳤으며, 나머지 60개를 제작 중입니다.",
                    "demo-manufacturing-1.png", -2),
            new SeedDetail("INSPECTION", 8, 10, "검수 예정입니다.", null, -4),
            new SeedDetail("SHIPPING_OUT", 11, 13, "출고 예정입니다.", null, -4),
            new SeedDetail("DELIVERY", 14, 17, "배송 예정입니다.", null, -4));

    /** 트래커 last_updated_at — 생산 기록일. 7일 안이라 미갱신 경고가 뜨지 않는다. */
    static final int LAST_UPDATED_DAYS = -2;

    private final FulfillmentTrackerJpaRepository trackerRepository;
    private final FulfillmentStageDetailJpaRepository stageDetailRepository;
    private final FulfillmentScheduleChangeJpaRepository scheduleChangeRepository;
    private final TransactionTemplate transactionTemplate;

    public DemoFulfillmentSeeder(FulfillmentTrackerJpaRepository trackerRepository,
                                 FulfillmentStageDetailJpaRepository stageDetailRepository,
                                 FulfillmentScheduleChangeJpaRepository scheduleChangeRepository,
                                 TransactionTemplate transactionTemplate) {
        this.trackerRepository = trackerRepository;
        this.stageDetailRepository = stageDetailRepository;
        this.scheduleChangeRepository = scheduleChangeRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            transactionTemplate.executeWithoutResult(status -> seed(Instant.now()));
        } catch (RuntimeException e) {
            log.warn("시연 제작·배송 시드 실패", e);
        }
    }

    void seed(Instant base) {
        trackerRepository.findByProjectPublicId(DEMO_PROJECT_ID).ifPresentOrElse(
                tracker -> refreshIfUntouched(tracker, base),
                () -> create(base));
    }

    private void create(Instant base) {
        FulfillmentTrackerJpaEntity tracker = trackerRepository.save(FulfillmentTrackerJpaEntity.builder()
                .projectPublicId(DEMO_PROJECT_ID)
                .currentStage(CURRENT_STAGE)
                .lastUpdatedAt(days(base, LAST_UPDATED_DAYS))
                .createdAt(days(base, -15))
                .build());
        insertRows(tracker.getId(), base);
        log.info("시연 제작·배송 시드 완료 projectPublicId={}", DEMO_PROJECT_ID);
    }

    /** 시드 모양 그대로(생산 단계, 기록 5건, 일정 변경 1건)일 때만 날짜를 다시 계산한다 — 판매자가 넣은 데이터는 지우지 않는다. */
    private void refreshIfUntouched(FulfillmentTrackerJpaEntity tracker, Instant base) {
        List<FulfillmentStageDetailJpaEntity> details = stageDetailRepository.findByTrackerIdOrderByUpdatedAtDesc(tracker.getId());
        List<FulfillmentScheduleChangeJpaEntity> changes = scheduleChangeRepository.findByTrackerIdOrderByChangedAtDesc(tracker.getId());
        if (!CURRENT_STAGE.equals(tracker.getCurrentStage()) || details.size() != DETAILS.size() || changes.size() != 1) {
            log.info("시연 제작·배송 데이터가 시연 중 변경돼 날짜를 갱신하지 않는다 stage={} details={} changes={}",
                    tracker.getCurrentStage(), details.size(), changes.size());
            return;
        }
        stageDetailRepository.deleteAll(details);
        scheduleChangeRepository.deleteAll(changes);
        insertRows(tracker.getId(), base);
        trackerRepository.save(FulfillmentTrackerJpaEntity.builder()
                .id(tracker.getId())
                .projectId(tracker.getProjectId())
                .projectPublicId(tracker.getProjectPublicId())
                .currentStage(tracker.getCurrentStage())
                .lastUpdatedAt(days(base, LAST_UPDATED_DAYS))
                .createdAt(tracker.getCreatedAt())
                .build());
        log.info("시연 제작·배송 날짜를 기동 시각 기준으로 갱신했다");
    }

    private void insertRows(Long trackerId, Instant base) {
        stageDetailRepository.saveAll(DETAILS.stream()
                .map(d -> FulfillmentStageDetailJpaEntity.builder()
                        .trackerId(trackerId)
                        .stage(d.stage())
                        .plannedStartAt(days(base, d.plannedStartDays()))
                        .plannedEndAt(days(base, d.plannedEndDays()))
                        .detailText(d.text())
                        .photoUrls(d.photo() == null ? List.of() : List.of(PHOTO_BASE + d.photo()))
                        .updatedAt(days(base, d.updatedDays()))
                        .build())
                .toList());
        scheduleChangeRepository.save(FulfillmentScheduleChangeJpaEntity.builder()
                .trackerId(trackerId)
                .stage(CURRENT_STAGE)
                .reasonType("STOCK_SHORTAGE")
                .reasonDetail("손잡이 자재 입고가 지연되어 생산 완료 예정일을 5일 연장했습니다. "
                        + "변경된 생산 일정에 맞춰 검수·출고·배송 계획도 조정했습니다.")
                .oldPlannedDate(days(base, 2))
                .newPlannedDate(days(base, 7))
                .changedAt(days(base, -4))
                .build());
    }

    private static Instant days(Instant base, int days) {
        return base.plus(Duration.ofDays(days));
    }
}

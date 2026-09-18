package com.fundit.order.infrastructure.migration;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * cross-service ID 통일(#69) 1회성 백필 진입점 — 기본은 비활성(운영에서 필요할 때만
 * {@code order.migration.backfill-funding-project-public-id.enabled=true}로 켜서 재기동 1회
 * 실행 후 다시 끈다). 영구 스케줄 잡이 아니다. 실패한 행이 있어도 재실행하면 idempotent하게
 * 남은 행만 다시 처리한다({@link FundingProjectPublicIdBackfillService} 참고).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order.migration.backfill-funding-project-public-id", name = "enabled",
        havingValue = "true")
public class FundingProjectPublicIdBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FundingProjectPublicIdBackfillRunner.class);

    private final FundingProjectPublicIdBackfillService backfillService;

    @Override
    public void run(ApplicationArguments args) {
        List<Long> targets = backfillService.findTargetFundingIds();
        log.info("[BACKFILL] fundings.project_public_id 백필 대상 {}건", targets.size());

        int success = 0;
        int failed = 0;
        for (Long fundingId : targets) {
            try {
                if (backfillService.backfillOne(fundingId)) {
                    success++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                log.error("[BACKFILL] fundingId={} 백필 실패", fundingId, e);
                failed++;
            }
        }
        log.info("[BACKFILL] 완료 — 성공 {}건, 실패(또는 publicId 조회 불가) {}건", success, failed);
    }
}

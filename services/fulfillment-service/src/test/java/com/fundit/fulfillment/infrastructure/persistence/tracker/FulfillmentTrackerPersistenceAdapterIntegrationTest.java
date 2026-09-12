package com.fundit.fulfillment.infrastructure.persistence.tracker;

import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * uq_fulfillment_trackers_project 유니크 제약이 실제 Postgres에서 의도대로 동작하는지 검증한다
 * (FULFILLMENT-001 idempotent 처리의 마지막 방어선 — test-convention.md "DB 제약조건 위반은
 * 통합 예외 테스트" 기준).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class FulfillmentTrackerPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private FulfillmentTrackerRepository trackerRepository;

    @Test
    void 같은_project_id로_두번_저장하면_두번째는_제약_위반_예외가_발생한다() {
        // given
        trackerRepository.save(FulfillmentTracker.create(999L));

        // when & then
        assertThatThrownBy(() -> trackerRepository.save(FulfillmentTracker.create(999L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 저장한_트래커를_project_id로_조회할_수_있다() {
        // given
        trackerRepository.save(FulfillmentTracker.create(1000L));

        // when
        var found = trackerRepository.findByProjectId(1000L);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getProjectId()).isEqualTo(1000L);
    }
}

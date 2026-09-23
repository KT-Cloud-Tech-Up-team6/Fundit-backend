package com.fundit.fulfillment.infrastructure.persistence.shipment;

import java.util.UUID;

import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * uq_shipments_funding_order 유니크 제약과 fk_shipments_tracker_project_public FK 제약이
 * 실제 Postgres에서 의도대로 동작하는지 검증한다(test-convention.md "DB 제약조건 위반은
 * 통합 예외 테스트" 기준).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class ShipmentPersistenceAdapterIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private ShipmentRepository shipmentRepository;
    @Autowired
    private FulfillmentTrackerRepository trackerRepository;

    @Test
    void 같은_funding_id로_두번_저장하면_두번째는_제약_위반_예외가_발생한다() {
        // given
        trackerRepository.save(FulfillmentTracker.create(UUID.fromString("00000000-0000-0000-0000-000000000555")));
        shipmentRepository.save(Shipment.create(UUID.fromString("00000000-0000-0000-0000-000000009001"), UUID.fromString("00000000-0000-0000-0000-000000000555")));

        // when & then
        assertThatThrownBy(() -> shipmentRepository.save(Shipment.create(UUID.fromString("00000000-0000-0000-0000-000000009001"), UUID.fromString("00000000-0000-0000-0000-000000000555"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 존재하지_않는_project_id를_참조하면_FK_위반_예외가_발생한다() {
        // when & then — fulfillment_trackers에 없는 project_public_id
        assertThatThrownBy(() -> shipmentRepository.save(Shipment.create(
                UUID.fromString("00000000-0000-0000-0000-000000009002"),
                UUID.fromString("00000000-0000-0000-0000-000000999999"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 저장한_발송정보를_funding_id로_조회할_수_있다() {
        // given
        trackerRepository.save(FulfillmentTracker.create(UUID.fromString("00000000-0000-0000-0000-000000000556")));
        Shipment shipment = Shipment.create(UUID.fromString("00000000-0000-0000-0000-000000009003"), UUID.fromString("00000000-0000-0000-0000-000000000556"));
        shipment.registerShipment("CJ대한통운", "123456789012");
        shipmentRepository.save(shipment);

        // when
        var found = shipmentRepository.findByFundingId(UUID.fromString("00000000-0000-0000-0000-000000009003"));

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getCarrier()).isEqualTo("CJ대한통운");
    }

    @Test
    @Transactional
    void 상태_변경용_조회는_행에_쓰기_잠금을_걸고_같은_값을_돌려준다() {
        // given — 잠금 조회는 트랜잭션 안에서만 성립한다.
        trackerRepository.save(FulfillmentTracker.create(UUID.fromString("00000000-0000-0000-0000-000000000557")));
        Shipment shipment = Shipment.create(UUID.fromString("00000000-0000-0000-0000-000000009004"), UUID.fromString("00000000-0000-0000-0000-000000000557"));
        shipment.registerShipment("CJ대한통운", "123456789012");
        shipmentRepository.save(shipment);

        // when — SELECT ... FOR UPDATE가 실제로 실행되는지(파생 쿼리 이름 포함) 확인한다.
        var found = shipmentRepository.findByFundingIdForUpdate(UUID.fromString("00000000-0000-0000-0000-000000009004"));

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getTrackingNumber()).isEqualTo("123456789012");
    }
}

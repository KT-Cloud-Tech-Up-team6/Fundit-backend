package com.fundit.fulfillment.domain.shipment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository {

    Optional<Shipment> findByFundingId(UUID fundingId);

    /**
     * 상태를 바꾸는 경로에서 쓰는 조회 — 행에 쓰기 잠금을 걸어, 읽고 고쳐 저장하는 사이에
     * 끼어든 다른 트랜잭션의 결과를 덮어쓰지 않게 한다. {@code save()}가 매퍼로 만든 엔티티
     * 전체를 저장하므로, 잠금이 없으면 오래된 스냅샷 하나가 상태 전이를 통째로 되돌린다
     * (예: 임시저장이 방금 커밋된 SHIPPED를 PREPARING으로 되돌림).
     *
     * <p>ponytail: 행이 아직 없는 경우는 잠글 대상이 없어 동시 생성이 유니크 인덱스
     * (uq_shipments_funding_order)에서 걸린다 — 실제로 부딪히면 그때 upsert로 바꿀 것.
     */
    Optional<Shipment> findByFundingIdForUpdate(UUID fundingId);

    /** payment-service/order-service 배치 조회용(FULFILLMENT-008 배치 변형). */
    List<Shipment> findByFundingIdIn(List<UUID> fundingIds);

    Shipment save(Shipment shipment);

    /** FULFILLMENT-007 배치 대상 조회 — 발송 후 threshold 이전에 발송된 SHIPPED 건. */
    List<Shipment> findByStatusAndShippedAtBefore(ShipmentStatus status, Instant threshold);

    /** FULFILLMENT-010 배치 대상 조회 — 배송완료 후 threshold 이전에 완료된 DELIVERED 건. */
    List<Shipment> findByStatusAndDeliveredAtBefore(ShipmentStatus status, Instant threshold);
}

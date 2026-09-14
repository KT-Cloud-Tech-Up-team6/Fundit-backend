package com.fundit.fulfillment.application.shipment;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.fulfillment.application.funding.OrderFundingClient;
import com.fundit.fulfillment.application.funding.OrderFundingClient.FundingSnapshot;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.domain.FulfillmentErrorCode;
import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * FULFILLMENT-006(발송정보 등록)/009(수령확인)/003(펀딩 단위 배송현황 조회, API #6).
 * {@code shipments}는 지연 생성 애그리거트 — 발송 등록 전까지 행 자체가 없다.
 */
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ProjectOwnershipClient projectOwnershipClient;
    private final OrderFundingClient orderFundingClient;

    /** FULFILLMENT-006 — API #5. */
    @Transactional
    public Shipment registerShipment(Long projectId, Long fundingId, UUID sellerId, String carrier,
                                      String trackingNumber) {
        verifyProjectOwnership(projectId, sellerId);
        verifyFundingBelongsToProject(fundingId, projectId);

        Shipment shipment = shipmentRepository.findByFundingId(fundingId)
                .orElseGet(() -> Shipment.create(fundingId, projectId));
        shipment.registerShipment(carrier, trackingNumber);
        return shipmentRepository.save(shipment);
    }

    /**
     * FULFILLMENT-003 — API #6. shipments 레코드가 없으면(아직 발송 전) 에러가 아니라 PREPARING
     * 상태의 가상 뷰를 그대로 반환한다(저장하지 않음).
     */
    @Transactional(readOnly = true)
    public Shipment getShipment(Long projectId, Long fundingId, UUID buyerId) {
        verifyFundingOwnership(fundingId, buyerId);
        return shipmentRepository.findByFundingId(fundingId)
                .orElseGet(() -> Shipment.create(fundingId, projectId));
    }

    /** FULFILLMENT-009 — API #7. */
    @Transactional
    public Shipment confirmReceipt(Long projectId, Long fundingId, UUID buyerId) {
        verifyFundingOwnership(fundingId, buyerId);
        // shipments 행 자체가 없으면(아직 발송 전) "배송완료 전" 상태와 동일하게 취급한다.
        Shipment shipment = shipmentRepository.findByFundingId(fundingId)
                .orElseThrow(() -> new BusinessException(FulfillmentErrorCode.NOT_YET_DELIVERED));
        shipment.confirmReceipt(Instant.now(), false);
        return shipmentRepository.save(shipment);
    }

    private void verifyProjectOwnership(Long projectId, UUID sellerId) {
        UUID actualSellerId = projectOwnershipClient.getSellerId(projectId);
        if (!actualSellerId.equals(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    /** 경로의 projectId가 실제로 그 funding의 프로젝트인지 order-service로 교차 검증한다[가정]. */
    private void verifyFundingBelongsToProject(Long fundingId, Long projectId) {
        FundingSnapshot snapshot = orderFundingClient.fetch(fundingId);
        if (!projectId.equals(snapshot.projectId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "해당 프로젝트에 속한 펀딩이 아닙니다.");
        }
    }

    private void verifyFundingOwnership(Long fundingId, UUID buyerId) {
        FundingSnapshot snapshot = orderFundingClient.fetch(fundingId);
        if (!buyerId.equals(snapshot.memberId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}

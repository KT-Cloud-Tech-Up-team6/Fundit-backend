package com.fundit.fulfillment.application.shipment;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.fulfillment.application.funding.FulfillmentDomainEventPublisher;
import com.fundit.fulfillment.application.funding.FulfillmentDomainEventPublisher.ShipmentShippedEvent;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FULFILLMENT-006(발송정보 등록·임시저장)/009(수령확인)/003(펀딩 단위 배송현황 조회, API #6).
 * {@code shipments}는 지연 생성 애그리거트 — 발송 등록 <b>또는 임시저장</b> 전까지 행 자체가 없다.
 */
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final ProjectOwnershipClient projectOwnershipClient;
    private final OrderFundingClient orderFundingClient;
    private final FulfillmentDomainEventPublisher domainEventPublisher;

    /**
     * FULFILLMENT-006 — API #5. {@code registerShipment}가 이미 SHIPPED 이상이면 예외를 던지므로
     * (`Shipment.registerShipment`), 이 메서드가 성공적으로 끝나는 건 PREPARING→SHIPPED 전이가
     * 실제로 일어난 경우뿐이다 — 매 호출마다 이벤트를 중복 발행할 걱정 없이 그대로 발행한다.
     */
    @Transactional
    public Shipment registerShipment(UUID projectId, UUID fundingId, UUID sellerId, String carrier,
                                      String trackingNumber) {
        verifyProjectOwnership(projectId, sellerId);
        verifyFundingBelongsToProject(fundingId, projectId);

        Shipment shipment = shipmentRepository.findByFundingId(fundingId)
                .orElseGet(() -> Shipment.create(fundingId, projectId));
        shipment.registerShipment(carrier, trackingNumber);
        Shipment saved = shipmentRepository.save(shipment);
        domainEventPublisher.publishShipmentShipped(new ShipmentShippedEvent(fundingId, projectId));
        return saved;
    }

    /**
     * FULFILLMENT-006 — 발송정보 화면의 "저장". 택배사·운송장만 적어 두고 발송 처리는 하지 않는다
     * (상태 PREPARING 유지, {@code shipment.shipped.v1} 미발행 → order-service 목록에서 계속
     * "발송 대기"). 실제 발송은 {@link #registerShipment}.
     */
    @Transactional
    public Shipment saveShippingInfo(UUID projectId, UUID fundingId, UUID sellerId, String carrier,
                                      String trackingNumber) {
        verifyProjectOwnership(projectId, sellerId);
        verifyFundingBelongsToProject(fundingId, projectId);

        Shipment shipment = shipmentRepository.findByFundingId(fundingId)
                .orElseGet(() -> Shipment.create(fundingId, projectId));
        shipment.saveShippingInfo(carrier, trackingNumber);
        return shipmentRepository.save(shipment);
    }

    /**
     * 판매자 발송 목록의 한 페이지를 한 번에 채우기 위한 배치 조회. 행마다 단건 조회를 돌면
     * 건당 소유권·펀딩 확인 호출이 2번씩 붙어 페이지 로드마다 내부 호출이 행 수만큼 불어난다.
     *
     * <p>요청한 fundingId 중 다른 프로젝트의 것은 조회 결과에서 걸러내고(경로 projectId 기준),
     * 아직 행이 없는 건은 단건 조회와 같은 규칙으로 PREPARING 가상 뷰를 돌려준다(저장하지 않음).
     */
    @Transactional(readOnly = true)
    public List<Shipment> listForSeller(UUID projectId, UUID sellerId, List<UUID> fundingIds) {
        verifyProjectOwnership(projectId, sellerId);
        if (fundingIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Shipment> found = shipmentRepository.findByFundingIdIn(fundingIds).stream()
                .filter(shipment -> projectId.equals(shipment.getProjectId()))
                .collect(Collectors.toMap(Shipment::getFundingId, Function.identity()));
        return fundingIds.stream()
                .map(fundingId -> found.getOrDefault(fundingId, Shipment.create(fundingId, projectId)))
                .toList();
    }

    /**
     * FULFILLMENT-003 — API #6. shipments 레코드가 없으면(아직 발송 전) 에러가 아니라 PREPARING
     * 상태의 가상 뷰를 그대로 반환한다(저장하지 않음).
     */
    @Transactional(readOnly = true)
    public Shipment getShipment(UUID projectId, UUID fundingId, UUID buyerId) {
        verifyFundingOwnershipInProject(fundingId, projectId, buyerId);
        return shipmentRepository.findByFundingId(fundingId)
                .orElseGet(() -> Shipment.create(fundingId, projectId));
    }

    /** FULFILLMENT-009 — API #7. */
    @Transactional
    public Shipment confirmReceipt(UUID projectId, UUID fundingId, UUID buyerId) {
        verifyFundingOwnershipInProject(fundingId, projectId, buyerId);
        // shipments 행 자체가 없으면(아직 발송 전) "배송완료 전" 상태와 동일하게 취급한다.
        Shipment shipment = shipmentRepository.findByFundingId(fundingId)
                .orElseThrow(() -> new BusinessException(FulfillmentErrorCode.NOT_YET_DELIVERED));
        shipment.confirmReceipt(Instant.now(), false);
        return shipmentRepository.save(shipment);
    }

    private void verifyProjectOwnership(UUID projectId, UUID sellerId) {
        UUID actualSellerId = projectOwnershipClient.getSellerId(projectId);
        if (!actualSellerId.equals(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }

    /** 경로의 projectId가 실제로 그 funding의 프로젝트인지 order-service로 교차 검증한다[가정]. */
    private void verifyFundingBelongsToProject(UUID fundingId, UUID projectId) {
        FundingSnapshot snapshot = orderFundingClient.fetch(fundingId);
        if (!projectId.equals(snapshot.projectId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "해당 프로젝트에 속한 펀딩이 아닙니다.");
        }
    }

    /**
     * 구매자 경로의 검증 — 소유자 확인만 하면 경로 projectId를 아무 값이나 넣어도 통과하고,
     * 그 값이 그대로 응답(가상 뷰)에 실린다. 스냅샷을 한 번만 읽어 둘 다 확인한다.
     */
    private void verifyFundingOwnershipInProject(UUID fundingId, UUID projectId, UUID buyerId) {
        FundingSnapshot snapshot = orderFundingClient.fetch(fundingId);
        if (!buyerId.equals(snapshot.memberId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (!projectId.equals(snapshot.projectId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "해당 프로젝트에 속한 펀딩이 아닙니다.");
        }
    }
}

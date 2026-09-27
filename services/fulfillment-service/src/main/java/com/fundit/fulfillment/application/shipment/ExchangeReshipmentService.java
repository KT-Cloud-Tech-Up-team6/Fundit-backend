package com.fundit.fulfillment.application.shipment;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 교환 재발송 착수 — payment-service가 교환을 승인(판매자 귀책)하거나 교환 배송비 결제가
 * 승인된 뒤 내부 API로 호출한다. 이 서비스는 교환 정책(누가 교환비를 내는지, 승인 여부)을
 * 판단하지 않는다 — 그건 payment-service 소관이고, 여기서는 "배송을 다시 시작한다"만 한다.
 *
 * <p>같은 교환 신청으로 두 번 호출돼도 상태를 다시 리셋하지 않는다({@code refundRequestId} 멱등).
 */
@Service
@RequiredArgsConstructor
public class ExchangeReshipmentService {

    private final ShipmentRepository shipmentRepository;

    @Transactional
    public ReshipmentResult startReshipment(UUID fundingId, Long refundRequestId) {
        // 발송 이력이 없는 펀딩에 교환이 생길 수 없다(교환은 수령 후 7일 이내 신청).
        Shipment shipment = shipmentRepository.findByFundingIdForUpdate(fundingId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        boolean started = shipment.startReshipment(refundRequestId, Instant.now());
        if (!started) {
            return new ReshipmentResult(shipment.getStatus().name(), shipment.getReshipmentCount());
        }
        Shipment saved = shipmentRepository.save(shipment);
        return new ReshipmentResult(saved.getStatus().name(), saved.getReshipmentCount());
    }

    public record ReshipmentResult(String status, int reshipmentCount) {
    }
}

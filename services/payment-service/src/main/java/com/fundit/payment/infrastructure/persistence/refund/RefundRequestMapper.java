package com.fundit.payment.infrastructure.persistence.refund;

import com.fundit.payment.domain.refund.AlternateRefundAccount;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestStatus;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.infrastructure.security.AesGcmCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
class RefundRequestMapper {

    private final AesGcmCipher cipher;
    private final ObjectMapper objectMapper;

    RefundRequest toDomain(RefundRequestJpaEntity entity) {
        return RefundRequest.builder()
                .id(entity.getId())
                .fundingId(entity.getFundingId())
                .paymentId(entity.getPaymentId())
                .triggerType(RefundTriggerType.valueOf(entity.getTriggerType()))
                .status(RefundRequestStatus.valueOf(entity.getStatus()))
                .isFullRefund(entity.getIsFullRefund())
                .reasonDetail(entity.getReasonDetail())
                .evidenceUrls(entity.getEvidenceUrls())
                .rejectedReason(entity.getRejectedReason())
                .alternateRefundAccount(decrypt(entity.getAlternateRefundAccountCipherText()))
                .requestedAt(entity.getRequestedAt())
                .processedAt(entity.getProcessedAt())
                .build();
    }

    RefundRequestJpaEntity toEntity(RefundRequest domain) {
        return RefundRequestJpaEntity.builder()
                .id(domain.getId())
                .fundingId(domain.getFundingId())
                .paymentId(domain.getPaymentId())
                .triggerType(domain.getTriggerType().name())
                .status(domain.getStatus().name())
                .isFullRefund(domain.getIsFullRefund())
                .reasonDetail(domain.getReasonDetail())
                .evidenceUrls(domain.getEvidenceUrls())
                .rejectedReason(domain.getRejectedReason())
                .alternateRefundAccountCipherText(encrypt(domain.getAlternateRefundAccount()))
                .requestedAt(domain.getRequestedAt())
                .processedAt(domain.getProcessedAt())
                .build();
    }

    private String encrypt(AlternateRefundAccount account) {
        if (account == null) {
            return null;
        }
        return cipher.encrypt(objectMapper.writeValueAsString(account));
    }

    private AlternateRefundAccount decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        return objectMapper.readValue(cipher.decrypt(cipherText), AlternateRefundAccount.class);
    }
}

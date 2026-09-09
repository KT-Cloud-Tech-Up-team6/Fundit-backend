package com.fundit.payment.infrastructure.persistence.refund;

import com.fundit.payment.domain.refund.AlternateRefundAccount;
import com.fundit.payment.domain.refund.RefundRequest;
import com.fundit.payment.domain.refund.RefundRequestStatus;
import com.fundit.payment.domain.refund.RefundTriggerType;
import com.fundit.payment.infrastructure.security.AesGcmCipher;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefundRequestMapperUnitTest {

    private final AesGcmCipher cipher = new AesGcmCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private final RefundRequestMapper mapper = new RefundRequestMapper(cipher, new ObjectMapper());

    @Test
    void 대체계좌가_있으면_암호화되어_왕복한다() {
        // given
        Instant now = Instant.parse("2026-09-08T01:00:00Z");
        AlternateRefundAccount account = new AlternateRefundAccount("국민", "홍길동", "123-456");
        RefundRequest domain = RefundRequest.builder()
                .id(3L)
                .fundingId(1024L)
                .paymentId(UUID.randomUUID())
                .triggerType(RefundTriggerType.GOAL_FAILED_AUTO)
                .status(RefundRequestStatus.REQUESTED)
                .isFullRefund(true)
                .reasonDetail("미달")
                .evidenceUrls(List.of("https://cdn/a.jpg"))
                .rejectedReason(null)
                .alternateRefundAccount(account)
                .requestedAt(now)
                .processedAt(now)
                .build();

        // when
        RefundRequestJpaEntity entity = mapper.toEntity(domain);
        RefundRequest restored = mapper.toDomain(entity);

        // then
        assertThat(entity.getAlternateRefundAccountCipherText()).isNotBlank();
        assertThat(entity.getAlternateRefundAccountCipherText()).doesNotContain("123-456");
        assertThat(restored.getAlternateRefundAccount()).isEqualTo(account);
        assertThat(restored.getTriggerType()).isEqualTo(RefundTriggerType.GOAL_FAILED_AUTO);
        assertThat(restored.getEvidenceUrls()).containsExactly("https://cdn/a.jpg");
    }

    @Test
    void 대체계좌가_없으면_암호문도_null이다() {
        RefundRequest domain = RefundRequest.requestDefect(1024L, UUID.randomUUID(), "파손", List.of("url"));

        RefundRequestJpaEntity entity = mapper.toEntity(domain);
        RefundRequest restored = mapper.toDomain(entity);

        assertThat(entity.getAlternateRefundAccountCipherText()).isNull();
        assertThat(restored.getAlternateRefundAccount()).isNull();
        assertThat(restored.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);
    }
}

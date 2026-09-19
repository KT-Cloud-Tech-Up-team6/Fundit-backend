package com.fundit.payment.application.refund;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.application.media.MediaStorageClient;
import com.fundit.payment.domain.PaymentErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundEvidenceUploadServiceUnitExceptionTest {

    @Mock
    private OrderFundingClient orderFundingClient;
    @Mock
    private MediaStorageClient storageClient;

    private RefundEvidenceUploadService service;

    @BeforeEach
    void setUp() {
        service = new RefundEvidenceUploadService(orderFundingClient, storageClient, 5);
    }

    @Test
    void 본인_주문이_아니면_FORBIDDEN_예외가_발생한다() {
        // given
        UUID orderId = UUID.randomUUID();
        lenient().when(orderFundingClient.fetch(orderId)).thenReturn(
                new OrderFundingClient.FundingSnapshot(UUID.randomUUID(), UUID.randomUUID(), "GOAL_ACHIEVED",
                        89_000L, "주문", null, orderId, 0L, 0L));

        // when & then
        assertThatThrownBy(() -> service.issueUploadUrl(UUID.randomUUID(), orderId, "e.jpg", "image/jpeg", 1024L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    }

    @Test
    void 허용되지_않은_확장자면_예외가_발생한다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderFundingClient.fetch(orderId)).thenReturn(
                new OrderFundingClient.FundingSnapshot(memberId, UUID.randomUUID(), "GOAL_ACHIEVED", 89_000L, "주문",
                        null, orderId, 0L, 0L));

        // when & then
        assertThatThrownBy(() -> service.issueUploadUrl(memberId, orderId, "evidence.exe", "application/exe", 1024L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void 파일_용량이_초과하면_예외가_발생한다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderFundingClient.fetch(orderId)).thenReturn(
                new OrderFundingClient.FundingSnapshot(memberId, UUID.randomUUID(), "GOAL_ACHIEVED", 89_000L, "주문",
                        null, orderId, 0L, 0L));

        // when & then
        assertThatThrownBy(() -> service.issueUploadUrl(memberId, orderId, "evidence.jpg", "image/jpeg",
                11L * 1024 * 1024))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(PaymentErrorCode.MEDIA_TOO_LARGE));
    }
}

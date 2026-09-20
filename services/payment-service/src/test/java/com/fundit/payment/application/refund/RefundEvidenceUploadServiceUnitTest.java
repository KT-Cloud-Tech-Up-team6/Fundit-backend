package com.fundit.payment.application.refund;

import com.fundit.payment.application.funding.OrderFundingClient;
import com.fundit.payment.application.media.MediaStorageClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundEvidenceUploadServiceUnitTest {

    @Mock
    private OrderFundingClient orderFundingClient;
    @Mock
    private MediaStorageClient storageClient;

    private RefundEvidenceUploadService service;

    private void setUpWithTtl(long ttlMinutes) {
        service = new RefundEvidenceUploadService(orderFundingClient, storageClient, ttlMinutes);
    }

    @Test
    void 본인_주문이면_증빙_업로드_주소를_발급한다() {
        // given
        setUpWithTtl(5);
        UUID memberId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderFundingClient.fetch(orderId)).thenReturn(
                new OrderFundingClient.FundingSnapshot(memberId, UUID.randomUUID(), "GOAL_ACHIEVED", 89_000L, "주문",
                        null, orderId, 0L, 0L));
        when(storageClient.presignPut(anyString(), eq("image/jpeg"), eq(Duration.ofMinutes(5))))
                .thenReturn(new MediaStorageClient.PresignedUpload("https://upload", "https://file"));

        // when
        MediaStorageClient.PresignedUpload result = service.issueUploadUrl(
                memberId, orderId, "evidence.jpg", "image/jpeg", 1024L);

        // then
        assertThat(result.uploadUrl()).isEqualTo("https://upload");
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageClient).presignPut(keyCaptor.capture(), eq("image/jpeg"), any());
        assertThat(keyCaptor.getValue()).startsWith("refunds/" + orderId + "/").endsWith(".jpg");
    }
}

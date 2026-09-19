package com.fundit.payment.application.refund;

import com.fundit.payment.infrastructure.persistence.refund.RefundRequestJpaRepository;
import com.fundit.payment.infrastructure.persistence.refund.query.RefundSummaryProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundQueryServiceUnitTest {

    @Mock
    private RefundRequestJpaRepository refundRequestJpaRepository;
    @Mock
    private OrderSummaryClient orderSummaryClient;

    private RefundQueryService refundQueryService;

    @BeforeEach
    void setUp() {
        refundQueryService = new RefundQueryService(refundRequestJpaRepository, orderSummaryClient);
    }

    @Test
    void 본인_환불내역을_프로젝션에서_뷰로_변환한다() {
        // given
        UUID memberId = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-09-08T01:00:00Z");
        RefundSummaryProjection projection = new RefundSummaryProjection() {
            @Override
            public Long getId() {
                return 3L;
            }

            @Override
            public UUID getFundingId() {
                return new UUID(0L, 1024L);
            }

            @Override
            public String getTriggerType() {
                return "DEFECT";
            }

            @Override
            public String getStatus() {
                return "REQUESTED";
            }

            @Override
            public long getAmount() {
                return 89_000L;
            }

            @Override
            public Instant getRequestedAt() {
                return requestedAt;
            }

            @Override
            public String getReasonDetail() {
                return "[DEFECTIVE] 파손됨";
            }

            @Override
            public String getRejectedReason() {
                return null;
            }

            @Override
            public Instant getProcessedAt() {
                return null;
            }
        };
        PageRequest pageable = PageRequest.of(0, 20);
        when(refundRequestJpaRepository.findSummariesByMemberId(memberId, pageable))
                .thenReturn(new PageImpl<>(List.of(projection), pageable, 1));
        when(orderSummaryClient.fetchBatch(List.of(new UUID(0L, 1024L)))).thenReturn(Map.of());

        // when
        var page = refundQueryService.listMyRefunds(memberId, pageable);

        // then
        assertThat(page.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.refundId()).isEqualTo(3L);
            assertThat(summary.fundingId()).isEqualTo(new UUID(0L, 1024L));
            assertThat(summary.triggerType()).isEqualTo("DEFECT");
            assertThat(summary.status()).isEqualTo("REQUESTED");
            assertThat(summary.amount()).isEqualTo(89_000L);
            assertThat(summary.requestedAt()).isEqualTo(requestedAt);
            assertThat(summary.reasonDetail()).isEqualTo("[DEFECTIVE] 파손됨");
            assertThat(summary.orderSummary()).isNull();
        });
    }

    @Test
    void order_service_조회에_성공하면_프로젝트명과_상품정보가_채워진다() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID fundingId = new UUID(0L, 2048L);
        RefundSummaryProjection projection = new RefundSummaryProjection() {
            public Long getId() { return 4L; }
            public UUID getFundingId() { return fundingId; }
            public String getTriggerType() { return "SIMPLE_CHANGE_OF_MIND"; }
            public String getStatus() { return "COMPLETED"; }
            public long getAmount() { return 10_000L; }
            public Instant getRequestedAt() { return Instant.now(); }
            public String getReasonDetail() { return null; }
            public String getRejectedReason() { return null; }
            public Instant getProcessedAt() { return Instant.now(); }
        };
        PageRequest pageable = PageRequest.of(0, 20);
        when(refundRequestJpaRepository.findSummariesByMemberId(memberId, pageable))
                .thenReturn(new PageImpl<>(List.of(projection), pageable, 1));
        when(orderSummaryClient.fetchBatch(List.of(fundingId))).thenReturn(Map.of(fundingId,
                new OrderSummaryClient.OrderSummary("프로젝트", List.of(new OrderSummaryClient.LineItem("리워드", 1, 10_000L)))));

        // when
        var page = refundQueryService.listMyRefunds(memberId, pageable);

        // then
        assertThat(page.getContent()).singleElement().satisfies(summary ->
                assertThat(summary.orderSummary().projectTitle()).isEqualTo("프로젝트"));
    }
}

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundQueryServiceUnitTest {

    @Mock
    private RefundRequestJpaRepository refundRequestJpaRepository;

    private RefundQueryService refundQueryService;

    @BeforeEach
    void setUp() {
        refundQueryService = new RefundQueryService(refundRequestJpaRepository);
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
            public Long getFundingId() {
                return 1024L;
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
        };
        PageRequest pageable = PageRequest.of(0, 20);
        when(refundRequestJpaRepository.findSummariesByMemberId(memberId, pageable))
                .thenReturn(new PageImpl<>(List.of(projection), pageable, 1));

        // when
        var page = refundQueryService.listMyRefunds(memberId, pageable);

        // then
        assertThat(page.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.refundId()).isEqualTo(3L);
            assertThat(summary.fundingId()).isEqualTo(1024L);
            assertThat(summary.triggerType()).isEqualTo("DEFECT");
            assertThat(summary.status()).isEqualTo("REQUESTED");
            assertThat(summary.amount()).isEqualTo(89_000L);
            assertThat(summary.requestedAt()).isEqualTo(requestedAt);
        });
    }
}

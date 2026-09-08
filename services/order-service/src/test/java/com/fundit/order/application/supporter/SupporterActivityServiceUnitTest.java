package com.fundit.order.application.supporter;

import com.fundit.order.application.member.MemberDisclosureClient;
import com.fundit.order.infrastructure.persistence.funding.FundingJpaRepository;
import com.fundit.order.infrastructure.persistence.funding.query.SupporterActivityProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupporterActivityServiceUnitTest {

    @Mock
    private FundingJpaRepository fundingJpaRepository;
    @Mock
    private MemberDisclosureClient memberDisclosureClient;

    @InjectMocks
    private SupporterActivityService supporterActivityService;

    private SupporterActivityProjection projection(UUID memberId, long amount) {
        return new SupporterActivityProjection() {
            @Override
            public UUID getMemberId() {
                return memberId;
            }

            @Override
            public Instant getCreatedAt() {
                return Instant.now().minusSeconds(60);
            }

            @Override
            public Long getAmount() {
                return amount;
            }
        };
    }

    @Test
    void 공개동의한_회원은_표시명과_금액을_노출한다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(fundingJpaRepository.findSupporterActivity(any(), any()))
                .thenReturn(new PageImpl<>(List.of(projection(memberId, 39_000L))));
        when(memberDisclosureClient.isPublicConsent(memberId)).thenReturn(true);

        // when
        var result = supporterActivityService.list(1L, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).singleElement().satisfies(activity -> {
            assertThat(activity.displayName()).isNotEqualTo("익명");
            assertThat(activity.amount()).isEqualTo(39_000L);
        });
    }

    @Test
    void 비공개_회원은_표시명과_금액이_마스킹된다() {
        // given
        UUID memberId = UUID.randomUUID();
        when(fundingJpaRepository.findSupporterActivity(any(), any()))
                .thenReturn(new PageImpl<>(List.of(projection(memberId, 39_000L))));
        when(memberDisclosureClient.isPublicConsent(memberId)).thenReturn(false);

        // when
        var result = supporterActivityService.list(1L, PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).singleElement().satisfies(activity -> {
            assertThat(activity.displayName()).isEqualTo("익명");
            assertThat(activity.amount()).isNull();
        });
    }
}

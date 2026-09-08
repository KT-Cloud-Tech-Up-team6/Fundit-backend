package com.fundit.order.domain.funding;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FundingUnitTest {

    private Funding newFunding() {
        List<FundingLineItem> lineItems = List.of(
                new FundingLineItem(null, 1L, "얼리버드 패키지", 2, 10_000L, List.of()));
        return Funding.create(UUID.randomUUID(), 10L, "세상에 없는 프라이팬",
                new ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시", "101동"),
                3_000L, lineItems, Instant.now().plusSeconds(3600));
    }

    @Test
    void 생성하면_PENDING_상태로_publicId가_발급된다() {
        // when
        Funding funding = newFunding();

        // then
        assertThat(funding.getStatus()).isEqualTo(FundingStatus.PENDING);
        assertThat(funding.getPublicId()).isNotNull();
    }

    @Test
    void 라인아이템_금액을_합산한다() {
        // when
        Funding funding = newFunding();

        // then — 10,000원 * 2개
        assertThat(funding.totalRewardAmount()).isEqualTo(20_000L);
    }

    @Nested
    class 참여_취소 {

        @Test
        void PENDING이면_취소된다() {
            // given
            Funding funding = newFunding();

            // when
            funding.cancelByMember();

            // then
            assertThat(funding.getStatus()).isEqualTo(FundingStatus.CANCELLED_BY_MEMBER);
            assertThat(funding.getDecidedAt()).isNotNull();
        }

        @Test
        void FUNDING_IN_PROGRESS면_취소된다() {
            // given
            Funding funding = newFunding();
            funding.markPaymentCompleted();

            // when
            funding.cancelByMember();

            // then
            assertThat(funding.getStatus()).isEqualTo(FundingStatus.CANCELLED_BY_MEMBER);
        }
    }

    @Nested
    class 미결제_만료 {

        @Test
        void PENDING이면_만료되고_true를_반환한다() {
            // given
            Funding funding = newFunding();

            // when
            boolean expired = funding.expireIfPending();

            // then
            assertThat(expired).isTrue();
            assertThat(funding.getStatus()).isEqualTo(FundingStatus.PAYMENT_EXPIRED);
        }

        @Test
        void PENDING이_아니면_아무것도_하지않고_false를_반환한다() {
            // given
            Funding funding = newFunding();
            funding.markPaymentCompleted();

            // when
            boolean expired = funding.expireIfPending();

            // then
            assertThat(expired).isFalse();
            assertThat(funding.getStatus()).isEqualTo(FundingStatus.FUNDING_IN_PROGRESS);
        }
    }

    @Nested
    class 목표달성_판정 {

        @Test
        void 달성하면_GOAL_ACHIEVED로_전이된다() {
            // given
            Funding funding = newFunding();

            // when
            funding.markGoalAchieved();

            // then
            assertThat(funding.getStatus()).isEqualTo(FundingStatus.GOAL_ACHIEVED);
            assertThat(funding.getDecidedAt()).isNotNull();
        }

        @Test
        void 미달하면_GOAL_FAILED_REFUNDED로_전이된다() {
            // given
            Funding funding = newFunding();

            // when
            funding.markGoalFailed();

            // then
            assertThat(funding.getStatus()).isEqualTo(FundingStatus.GOAL_FAILED_REFUNDED);
        }
    }

    @Nested
    class 결제완료_처리 {

        @Test
        void PENDING이면_FUNDING_IN_PROGRESS로_전이된다() {
            // given
            Funding funding = newFunding();

            // when
            funding.markPaymentCompleted();

            // then
            assertThat(funding.getStatus()).isEqualTo(FundingStatus.FUNDING_IN_PROGRESS);
        }

        @Test
        void PENDING이_아니면_무시한다() {
            // given
            Funding funding = newFunding();
            funding.cancelByMember();

            // when
            funding.markPaymentCompleted();

            // then
            assertThat(funding.getStatus()).isEqualTo(FundingStatus.CANCELLED_BY_MEMBER);
        }
    }

    @Nested
    class 가능한_액션 {

        @Test
        void PENDING이면_CANCEL만_가능하다() {
            assertThat(newFunding().availableActions()).containsExactly("CANCEL");
        }

        @Test
        void GOAL_ACHIEVED면_배송지연환불요청이_가능하다() {
            // given
            Funding funding = newFunding();

            // when
            funding.markGoalAchieved();

            // then
            assertThat(funding.availableActions()).containsExactly("SHIPPING_DELAY_REFUND_REQUEST");
        }

        @Test
        void 취소된_주문은_가능한_액션이_없다() {
            // given
            Funding funding = newFunding();

            // when
            funding.cancelByMember();

            // then
            assertThat(funding.availableActions()).isEmpty();
        }
    }
}

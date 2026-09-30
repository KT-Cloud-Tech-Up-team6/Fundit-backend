package com.fundit.order.domain.funding;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FundingUnitTest {

    private Funding newFunding() {
        List<FundingLineItem> lineItems = List.of(
                new FundingLineItem(null, 1L, "얼리버드 패키지", 2, 10_000L, List.of()));
        return Funding.create(UUID.randomUUID(), UUID.randomUUID(), "세상에 없는 프라이팬",
                new ShippingAddress("홍길동", "010-1234-5678", "12345", "서울시", "101동"),
                3_000L, lineItems, Instant.now().plusSeconds(3600), null, null, null);
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
            funding.cancelByMember(null, null);

            // then
            assertThat(funding.getStatus()).isEqualTo(FundingStatus.CANCELLED_BY_MEMBER);
            assertThat(funding.getDecidedAt()).isNotNull();
        }

        @Test
        void FUNDING_IN_PROGRESS면_취소된다() {
            // given
            Funding funding = newFunding();
            funding.markPaymentCompleted(null);

            // when
            funding.cancelByMember(null, null);

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
            funding.markPaymentCompleted(null);

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
            funding.markPaymentCompleted(null);

            // then
            assertThat(funding.getStatus()).isEqualTo(FundingStatus.FUNDING_IN_PROGRESS);
        }

        @Test
        void PENDING이_아니면_무시한다() {
            // given
            Funding funding = newFunding();
            funding.cancelByMember(null, null);

            // when
            funding.markPaymentCompleted(null);

            // then
            assertThat(funding.getStatus()).isEqualTo(FundingStatus.CANCELLED_BY_MEMBER);
        }
    }

    @Nested
    class 가능한_액션 {

        @Test
        void PENDING이면_CANCEL만_가능하다() {
            assertThat(newFunding().availableActions(false, false, null)).containsExactly("CANCEL");
        }

        @Test
        void GOAL_ACHIEVED이고_발송전이며_발송예정일이_지났으면_배송지연환불요청이_가능하다() {
            // given
            Funding funding = newFunding();

            // when
            funding.markGoalAchieved();

            // then
            assertThat(funding.availableActions(false, true, null))
                    .containsExactly("SHIPPING_DELAY_REFUND_REQUEST");
        }

        @Test
        void GOAL_ACHIEVED이고_발송전이어도_발송예정일_이내면_가능한_액션이_없다() {
            // given
            Funding funding = newFunding();

            // when
            funding.markGoalAchieved();

            // then — 지연 전에는 신청해도 payment-service가 NOT_YET_DELAYED(422)로 막는다.
            assertThat(funding.availableActions(false, false, null)).isEmpty();
        }

        @Test
        void GOAL_ACHIEVED이고_배송완료_7일_이내면_반품_교환_하자환불_요청이_가능하다() {
            // given
            Funding funding = newFunding();

            // when
            funding.markGoalAchieved();

            // then
            assertThat(funding.availableActions(true, false, Instant.now().minus(Duration.ofDays(3))))
                    .containsExactly("RETURN_REQUEST", "EXCHANGE_REQUEST", "DEFECT_REFUND_REQUEST");
        }

        @Test
        void 배송완료_후_7일이_지나면_반품_교환_요청이_불가하다() {
            // given
            Funding funding = newFunding();

            // when
            funding.markGoalAchieved();

            // then — 환불 정책 V.1.0 "수령 후 7일 이내 신청"
            assertThat(funding.availableActions(true, false, Instant.now().minus(Duration.ofDays(8)))).isEmpty();
        }

        @Test
        void GOAL_ACHIEVED이고_발송중이면_가능한_액션이_없다() {
            // given
            Funding funding = newFunding();

            // when
            funding.markGoalAchieved();

            // then
            assertThat(funding.availableActions(true, false, null)).isEmpty();
        }

        @Test
        void 취소된_주문은_가능한_액션이_없다() {
            // given
            Funding funding = newFunding();

            // when
            funding.cancelByMember(null, null);

            // then
            assertThat(funding.availableActions(false, false, null)).isEmpty();
        }
    }

    @Nested
    class 진행_단계 {

        @Test
        void PENDING과_모금중은_펀딩_진행중이다() {
            Funding funding = newFunding();
            assertThat(funding.progressStage(false, false, null, false))
                    .isEqualTo(FundingProgressStage.FUNDING_IN_PROGRESS);

            funding.markPaymentCompleted(Instant.now());

            assertThat(funding.progressStage(false, false, null, false))
                    .isEqualTo(FundingProgressStage.FUNDING_IN_PROGRESS);
        }

        @Test
        void 성립_후_배송상태에_따라_성공_지연_배송중_완료로_갈린다() {
            // given
            Funding funding = newFunding();
            funding.markGoalAchieved();

            // then
            assertThat(funding.progressStage(false, false, null, false))
                    .isEqualTo(FundingProgressStage.FUNDING_SUCCEEDED);
            assertThat(funding.progressStage(false, true, null, false)).isEqualTo(FundingProgressStage.SHIPPING_DELAYED);
            assertThat(funding.progressStage(true, false, null, false)).isEqualTo(FundingProgressStage.SHIPPING);
            assertThat(funding.progressStage(true, false, Instant.now(), false))
                    .isEqualTo(FundingProgressStage.DELIVERED);
        }

        @Test
        void 성립_후_판매자의_진행_기록이_있으면_제작중이다() {
            // given
            Funding funding = newFunding();
            funding.markGoalAchieved();

            // then — 기록이 없으면 "펀딩 성공", 한 번이라도 올라오면 "제작 중"
            assertThat(funding.progressStage(false, false, null, false))
                    .isEqualTo(FundingProgressStage.FUNDING_SUCCEEDED);
            assertThat(funding.progressStage(false, false, null, true))
                    .isEqualTo(FundingProgressStage.IN_PRODUCTION);
        }

        @Test
        void 진행_기록이_있어도_발송지연과_발송완료가_제작중보다_우선이다() {
            // given
            Funding funding = newFunding();
            funding.markGoalAchieved();

            // then — 참여 취소(발송지연 환불)가 가능한 상태를 배지가 가려선 안 된다
            assertThat(funding.progressStage(false, true, null, true))
                    .isEqualTo(FundingProgressStage.SHIPPING_DELAYED);
            assertThat(funding.progressStage(true, false, null, true)).isEqualTo(FundingProgressStage.SHIPPING);
            assertThat(funding.progressStage(true, false, Instant.now(), true))
                    .isEqualTo(FundingProgressStage.DELIVERED);
        }

        @Test
        void 진행_기록은_성립_전후_다른_상태의_배지를_바꾸지_않는다() {
            Funding funding = newFunding();
            assertThat(funding.progressStage(false, false, null, true))
                    .isEqualTo(FundingProgressStage.FUNDING_IN_PROGRESS);
        }

        @Test
        void 목표미달과_취소는_각각의_단계를_돌려준다() {
            Funding failed = newFunding();
            failed.markGoalFailed();
            assertThat(failed.progressStage(false, false, null, false)).isEqualTo(FundingProgressStage.GOAL_FAILED);

            Funding cancelled = newFunding();
            cancelled.cancelByMember(null, null);
            assertThat(cancelled.progressStage(false, false, null, false)).isEqualTo(FundingProgressStage.CANCELLED);
        }
    }

    @Nested
    class 결제일과_취소사유 {

        @Test
        void 결제완료_시각을_저장한다() {
            // given
            Funding funding = newFunding();
            Instant paidAt = Instant.now();

            // when
            funding.markPaymentCompleted(paidAt);

            // then
            assertThat(funding.getPaidAt()).isEqualTo(paidAt);
        }

        @Test
        void 결제일이_없는_이벤트는_기존_값을_덮지_않는다() {
            // given
            Funding funding = newFunding();
            Instant paidAt = Instant.now();
            funding.markPaymentCompleted(paidAt);

            // when — 구버전 메시지 재전달
            funding.markPaymentCompleted(null);

            // then
            assertThat(funding.getPaidAt()).isEqualTo(paidAt);
        }

        @Test
        void 취소_사유를_저장한다() {
            // given
            Funding funding = newFunding();

            // when
            funding.cancelByMember(CancelReason.ETC, "배송지를 잘못 입력했어요");

            // then
            assertThat(funding.getCancelReason()).isEqualTo(CancelReason.ETC);
            assertThat(funding.getCancelReasonDetail()).isEqualTo("배송지를 잘못 입력했어요");
        }
    }
}

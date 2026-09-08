package com.fundit.order.domain.funding;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.order.domain.OrderErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundingUnitExceptionTest {

    private Funding newFunding() {
        List<FundingLineItem> lineItems = List.of(new FundingLineItem(null, 1L, "리워드", 1, 10_000L, List.of()));
        return Funding.create(UUID.randomUUID(), 10L, "프로젝트", new ShippingAddress("홍길동", "010", "12345", "주소", null),
                3_000L, lineItems, Instant.now().plusSeconds(3600));
    }

    @Test
    void 만료된_주문을_취소하려하면_RESOURCE_EXPIRED_예외가_발생한다() {
        // given
        Funding funding = newFunding();
        funding.expireIfPending();

        // when & then
        assertThatThrownBy(funding::cancelByMember)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.RESOURCE_EXPIRED));
    }

    @Test
    void 이미_취소된_주문을_다시_취소하려하면_ORDER_NOT_CANCELLABLE_예외가_발생한다() {
        // given
        Funding funding = newFunding();
        funding.cancelByMember();

        // when & then
        assertThatThrownBy(funding::cancelByMember)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_CANCELLABLE));
    }

    @Test
    void 목표달성_판정이_끝난_주문을_취소하려하면_ORDER_NOT_CANCELLABLE_예외가_발생한다() {
        // given
        Funding funding = newFunding();
        funding.markGoalAchieved();

        // when & then
        assertThatThrownBy(funding::cancelByMember)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_CANCELLABLE));
    }
}

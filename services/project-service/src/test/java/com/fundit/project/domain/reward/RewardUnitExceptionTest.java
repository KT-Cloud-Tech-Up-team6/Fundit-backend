package com.fundit.project.domain.reward;

import com.fundit.common.error.BusinessException;
import com.fundit.project.domain.ProjectErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RewardUnitExceptionTest {

    @Test
    void 한정수량인데_수량이_없으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> Reward.create(1L, "얼리버드", "설명", null, 39000L, true, null, false, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_REWARD_QUANTITY);
    }

    @Test
    void 무제한인데_수량이_있으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> Reward.create(1L, "얼리버드", "설명", null, 39000L, false, 10, false, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_REWARD_QUANTITY);
    }

    @Test
    void 수정시에도_수량_정합성을_재검증한다() {
        // given
        Reward reward = Reward.create(1L, "얼리버드", "설명", null, 39000L, false, null, false, null, null, null, null, null);

        // when & then
        assertThatThrownBy(() -> reward.changeBasicInfo("이름", "설명", null, 39000L, true, null, false, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_REWARD_QUANTITY);
    }

    @Test
    void 얼리버드인데_할인정보가_없으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> Reward.create(1L, "얼리버드", "설명", null, 39000L, false, null, true, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_EARLY_BIRD_DISCOUNT);
    }

    @Test
    void 얼리버드가_아닌데_할인정보가_있으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> Reward.create(1L, "얼리버드", "설명", null, 39000L, false, null, false,
                EarlyBirdDiscountType.RATE, 10L, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_EARLY_BIRD_DISCOUNT);
    }

    @Test
    void 정액할인이_가격_이상이면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> Reward.create(1L, "얼리버드", "설명", null, 39000L, false, null, true,
                EarlyBirdDiscountType.AMOUNT, 39000L, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_EARLY_BIRD_DISCOUNT);
    }

    @Test
    void 정률할인이_100을_초과하면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> Reward.create(1L, "얼리버드", "설명", null, 39000L, false, null, true,
                EarlyBirdDiscountType.RATE, 101L, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_EARLY_BIRD_DISCOUNT);
    }

    @Test
    void 배송비가_음수이면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> Reward.create(1L, "얼리버드", "설명", null, 39000L, false, null, false,
                null, null, null, -1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_REWARD_SHIPPING_INFO);
    }

    @Test
    void 예상_발송일이_음수이면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> Reward.create(1L, "얼리버드", "설명", null, 39000L, false, null, false,
                null, null, null, null, -1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_REWARD_SHIPPING_INFO);
    }
}

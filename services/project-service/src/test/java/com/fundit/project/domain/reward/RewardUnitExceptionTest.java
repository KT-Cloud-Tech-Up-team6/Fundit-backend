package com.fundit.project.domain.reward;

import com.fundit.common.error.BusinessException;
import com.fundit.project.domain.ProjectErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RewardUnitExceptionTest {

    @Test
    void 한정수량인데_수량이_없으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> Reward.create(1L, "얼리버드", "설명", null, 39000L, true, null, false, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_REWARD_QUANTITY);
    }

    @Test
    void 무제한인데_수량이_있으면_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> Reward.create(1L, "얼리버드", "설명", null, 39000L, false, 10, false, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_REWARD_QUANTITY);
    }

    @Test
    void 수정시에도_수량_정합성을_재검증한다() {
        // given
        Reward reward = Reward.create(1L, "얼리버드", "설명", null, 39000L, false, null, false, null);

        // when & then
        assertThatThrownBy(() -> reward.changeBasicInfo("이름", "설명", null, 39000L, true, null, false, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ProjectErrorCode.INVALID_REWARD_QUANTITY);
    }
}

package com.fundit.project.domain.reward;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.fixture.RewardFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.fundit.project.support.BusinessExceptionAssertions.assertBusinessException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Reward 도메인 예외")
class RewardUnitExceptionTest {

    @Nested
    class 생성 {

        @Test
        void 가격이_음수면_예외가_발생한다() {
            // given & when & then
            assertBusinessException(
                    () -> Reward.create(ProjectFixture.PROJECT_ID, "이름", null, -1L,
                            false, false, false, null, null),
                    CommonErrorCode.INVALID_INPUT);
        }

        @Test
        void 가격이_비어_있으면_예외가_발생한다() {
            // given & when & then
            assertBusinessException(
                    () -> Reward.create(ProjectFixture.PROJECT_ID, "이름", null, null,
                            false, false, false, null, null),
                    CommonErrorCode.INVALID_INPUT);
        }
    }

    @Nested
    class 수정 {

        @Test
        void 가격을_음수로_바꾸려_하면_예외가_발생한다() {
            // given
            Reward reward = RewardFixture.reward();

            // when & then
            assertBusinessException(
                    () -> reward.update(null, null, -1L, null, null, null, null, null),
                    CommonErrorCode.INVALID_INPUT);
        }

        @Test
        void 가격_검증에_실패하면_다른_필드도_반영되지_않는다() {
            // given
            Reward reward = RewardFixture.reward();

            // when
            assertThatThrownBy(() -> reward.update("바뀐 이름", null, -1L, null, null, null, null, null))
                    .isInstanceOf(BusinessException.class);

            // then
            assertThat(reward.getName()).isEqualTo("가습기 기본형");
            assertThat(reward.getPrice()).isEqualTo(39_000L);
        }
    }
}

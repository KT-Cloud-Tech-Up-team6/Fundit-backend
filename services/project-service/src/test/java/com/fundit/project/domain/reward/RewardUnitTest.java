package com.fundit.project.domain.reward;

import com.fundit.project.fixture.ProjectFixture;
import com.fundit.project.fixture.RewardFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Reward 도메인")
class RewardUnitTest {

    @Nested
    class 생성 {

        @Test
        void 전달한_값이_그대로_담긴다() {
            // given & when
            Reward reward = Reward.create(ProjectFixture.PROJECT_ID, "가습기 기본형", "설명", 39_000L,
                    false, true, false, "ELECTRONICS", Map.of("모델명", "H-100"));

            // then
            assertThat(reward.getProjectId()).isEqualTo(ProjectFixture.PROJECT_ID);
            assertThat(reward.getName()).isEqualTo("가습기 기본형");
            assertThat(reward.getPrice()).isEqualTo(39_000L);
            assertThat(reward.isUnlimited()).isFalse();
            assertThat(reward.isEarlyBird()).isTrue();
        }

        @Test
        void 가격이_0원이어도_등록된다() {
            // given & when
            Reward reward = Reward.create(ProjectFixture.PROJECT_ID, "무료 체험", null, 0L,
                    true, false, false, null, null);

            // then
            assertThat(reward.getPrice()).isZero();
        }
    }

    @Nested
    class 수정 {

        @Test
        void 전달한_값만_반영되고_생략한_값은_유지된다() {
            // given
            Reward reward = RewardFixture.reward();

            // when
            reward.update("바뀐 이름", null, null, null, null, null, null, null);

            // then
            assertThat(reward.getName()).isEqualTo("바뀐 이름");
            assertThat(reward.getPrice()).isEqualTo(39_000L);
            assertThat(reward.getDescription()).isEqualTo("설명");
        }

        @Test
        void 노출_순서를_바꿀_수_있다() {
            // given
            Reward reward = RewardFixture.reward();

            // when
            reward.update(null, null, null, null, null, null, null, 3);

            // then
            assertThat(reward.getDisplayOrder()).isEqualTo(3);
        }

        @Test
        void 무제한_여부는_수정_대상에_포함되지_않는다() {
            // given
            Reward reward = RewardFixture.unlimitedReward();

            // when
            reward.update("이름만 변경", null, null, null, null, null, null, null);

            // then
            assertThat(reward.isUnlimited()).isTrue();
        }

        @Test
        void 단순변심_환불불가를_켤_수_있다() {
            // given
            Reward reward = RewardFixture.reward();

            // when
            reward.update(null, null, null, null, true, null, null, null);

            // then
            assertThat(reward.isSimpleRefundDisabled()).isTrue();
        }
    }

    @Nested
    class 삭제 {

        @Test
        void 삭제_시각이_기록된다() {
            // given
            Reward reward = RewardFixture.reward();
            Instant now = Instant.parse("2026-08-15T00:00:00Z");

            // when
            reward.softDelete(now);

            // then
            assertThat(reward.getDeletedAt()).isEqualTo(now);
        }
    }
}

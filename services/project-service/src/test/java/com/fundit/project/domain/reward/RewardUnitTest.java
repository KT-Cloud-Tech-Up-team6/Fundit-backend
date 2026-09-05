package com.fundit.project.domain.reward;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RewardUnitTest {

    @Nested
    class 생성 {

        @Test
        void 한정수량이면_수량과_함께_생성된다() {
            // when
            Reward reward = Reward.create(1L, "얼리버드", "설명", null, 39000L, true, 100, true, null);

            // then
            assertThat(reward.isLimited()).isTrue();
            assertThat(reward.getQuantity()).isEqualTo(100);
            assertThat(reward.isHasOption()).isFalse();
        }

        @Test
        void 무제한이면_수량없이_생성된다() {
            // when
            Reward reward = Reward.create(1L, "얼리버드", "설명", null, 39000L, false, null, false, null);

            // then
            assertThat(reward.isLimited()).isFalse();
            assertThat(reward.getQuantity()).isNull();
        }

        @Test
        void 옵션이_있으면_hasOption이_true가된다() {
            // given
            List<RewardOptionGroup> options = List.of(new RewardOptionGroup("색상", List.of("화이트", "블랙")));

            // when
            Reward reward = Reward.create(1L, "얼리버드", "설명", null, 39000L, false, null, false, options);

            // then
            assertThat(reward.isHasOption()).isTrue();
            assertThat(reward.getOptionGroups()).hasSize(1);
        }
    }

    @Nested
    class 기본정보_변경 {

        @Test
        void 옵션을_전달하지_않으면_hasOption이_바뀌지_않는다() {
            // given
            Reward reward = Reward.create(1L, "얼리버드", "설명", null, 39000L, false, null, false, null);

            // when
            reward.changeBasicInfo("새이름", "새설명", null, 40000L, false, null, true, null);

            // then
            assertThat(reward.getName()).isEqualTo("새이름");
            assertThat(reward.isHasOption()).isFalse();
        }
    }

    @Test
    void 고시정보를_변경한다() {
        // given
        Reward reward = Reward.create(1L, "얼리버드", "설명", null, 39000L, false, null, false, null);

        // when
        reward.changeDisclosure("COSMETIC", Map.of("제조국", "대한민국"));

        // then
        assertThat(reward.getCategoryType()).isEqualTo("COSMETIC");
        assertThat(reward.getDisclosure()).containsEntry("제조국", "대한민국");
    }

    @Test
    void 환불정책_특이사항을_변경한다() {
        // given
        Reward reward = Reward.create(1L, "얼리버드", "설명", null, 39000L, false, null, false, null);

        // when
        reward.changeRefundPolicy(true);

        // then
        assertThat(reward.isSimpleRefundDisabled()).isTrue();
    }

    @Test
    void 삭제하면_deletedAt이_채워진다() {
        // given
        Reward reward = Reward.create(1L, "얼리버드", "설명", null, 39000L, false, null, false, null);

        // when
        reward.delete();

        // then
        assertThat(reward.isDeleted()).isTrue();
    }
}

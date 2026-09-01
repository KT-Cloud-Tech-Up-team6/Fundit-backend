package com.fundit.project.domain.reward;

import com.fundit.project.fixture.RewardFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RewardOption 도메인")
class RewardOptionUnitTest {

    @Nested
    class 생성 {

        @Test
        void 재고_수량은_보관하지_않는다() {
            // given & when
            RewardOption option = RewardOption.create(RewardFixture.REWARD_ID, "화이트", RewardFixture.SKU);

            // then — 재고는 order-service inventories가 소유하므로 이 애그리거트에 필드 자체가 없다
            assertThat(option.getOptionName()).isEqualTo("화이트");
            assertThat(option.getSku()).isEqualTo(RewardFixture.SKU);
        }
    }

    @Nested
    class 옵션명_변경 {

        @Test
        void 새_이름으로_바뀐다() {
            // given
            RewardOption option = RewardFixture.option();

            // when
            option.rename("블랙");

            // then
            assertThat(option.getOptionName()).isEqualTo("블랙");
        }

        @Test
        void null을_주면_기존_이름이_유지된다() {
            // given
            RewardOption option = RewardFixture.option();

            // when
            option.rename(null);

            // then
            assertThat(option.getOptionName()).isEqualTo("화이트");
        }

        @Test
        void sku는_바뀌지_않는다() {
            // given
            RewardOption option = RewardFixture.option();

            // when
            option.rename("블랙");

            // then — sku는 order-service가 재고 식별자로 참조 중이라 변경 수단을 두지 않는다
            assertThat(option.getSku()).isEqualTo(RewardFixture.SKU);
        }
    }

    @Nested
    class 삭제 {

        @Test
        void 삭제_시각이_기록된다() {
            // given
            RewardOption option = RewardFixture.option();
            Instant now = Instant.parse("2026-08-15T00:00:00Z");

            // when
            option.softDelete(now);

            // then
            assertThat(option.getDeletedAt()).isEqualTo(now);
        }
    }
}

package com.fundit.live.domain.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveSessionUnitExceptionTest {

    private static LiveSession live() {
        LiveSession session = LiveSession.create(1L, UUID.randomUUID());
        session.start(Instant.parse("2026-09-10T11:00:00Z"), "arn:chat");
        return session;
    }

    @Test
    void 진행중인_방송은_설정을_바꿀_수_없다() {
        // given
        LiveSession session = live();

        // when & then
        assertThatThrownBy(() -> session.updateSettings(null, null, "수정", null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);
    }

    @Test
    void 이미_시작된_방송은_다시_시작할_수_없다() {
        // given
        LiveSession session = live();

        // when & then
        assertThatThrownBy(() -> session.start(Instant.parse("2026-09-10T12:00:00Z"), "arn:chat"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 종료된_방송은_설정도_시작도_불가다() {
        // given
        LiveSession session = live();
        session.end(Instant.parse("2026-09-10T11:10:00Z"));

        // when & then
        assertThatThrownBy(() -> session.start(Instant.parse("2026-09-10T12:00:00Z"), "arn:chat"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> session.updateSettings(null, null, "수정", null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 종료된_방송은_오류로_뒤집을_수_없다() {
        // given — 송출 실패 기록이 끝난 방송의 상태를 덮으면 ENDED가 ERROR로 바뀐다
        LiveSession session = live();
        session.end(Instant.parse("2026-09-10T11:10:00Z"));

        // when & then
        assertThatThrownBy(() -> session.markError("채팅방 생성 실패",
                Instant.parse("2026-09-10T11:20:00Z")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);
        assertThat(session.getStatus()).isEqualTo(LiveStatus.ENDED);
    }

    @Test
    void 시작하지_않은_방송은_종료할_수_없다() {
        // given
        LiveSession session = LiveSession.create(1L, UUID.randomUUID());

        // when & then
        assertThatThrownBy(() -> session.end(Instant.parse("2026-09-10T11:10:00Z")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.CONFLICT);
    }
}

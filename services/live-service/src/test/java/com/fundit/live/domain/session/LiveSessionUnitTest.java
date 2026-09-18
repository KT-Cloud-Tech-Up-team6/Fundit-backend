package com.fundit.live.domain.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiveSessionUnitTest {

    private static LiveSession draft() {
        return LiveSession.create(1L, UUID.randomUUID());
    }

    @Test
    void 생성_직후_상태는_DRAFT다() {
        // given & when
        LiveSession session = draft();

        // then — 예정 시각을 다음 단계에서 받으므로 여기서 SCHEDULED로 두면
        // 예정 시각 없는 예약 상태가 된다.
        assertThat(session.getStatus()).isEqualTo(LiveStatus.DRAFT);
        assertThat(session.getPublicId()).isNotNull();
        assertThat(session.isPubliclyVisible()).isFalse();
    }

    @Nested
    @DisplayName("설정 업데이트")
    class 설정 {

        @Test
        void 예정시각이_채워지면_SCHEDULED로_올라간다() {
            // given
            LiveSession session = draft();

            // when
            session.updateSettings(null, null, null, null, Instant.parse("2026-09-10T11:00:00Z"));

            // then
            assertThat(session.getStatus()).isEqualTo(LiveStatus.SCHEDULED);
            assertThat(session.isPubliclyVisible()).isTrue();
        }

        @Test
        void null_필드는_기존값을_건드리지_않는다() {
            // given — 임시저장을 이어서 작성하는 화면이라 매번 전체를 보내지 않는다
            LiveSession session = draft();
            session.updateSettings("테크·가전", "생활가전", "첫 소개", "https://img/1.png", null);

            // when
            session.updateSettings(null, null, "바뀐 소개", null, null);

            // then
            assertThat(session.getIntroText()).isEqualTo("바뀐 소개");
            assertThat(session.getCategoryMajor()).isEqualTo("테크·가전");
            assertThat(session.getThumbnailUrl()).isEqualTo("https://img/1.png");
        }
    }

    @Nested
    @DisplayName("송출")
    class 송출 {

        @Test
        void 시작하면_LIVE로_전이하고_시작시각을_남긴다() {
            // given
            LiveSession session = draft();
            Instant now = Instant.parse("2026-09-10T11:00:00Z");

            // when
            session.start(now, "arn:chat");

            // then
            assertThat(session.getStatus()).isEqualTo(LiveStatus.LIVE);
            assertThat(session.getActualStartAt()).isEqualTo(now);
        }

        @Test
        void 종료하면_ENDED로_전이하고_종료시각을_남긴다() {
            // given
            LiveSession session = draft();
            session.start(Instant.parse("2026-09-10T11:00:00Z"), "arn:chat");
            Instant end = Instant.parse("2026-09-10T11:10:00Z");

            // when
            session.end(end);

            // then
            assertThat(session.getStatus()).isEqualTo(LiveStatus.ENDED);
            assertThat(session.getActualEndAt()).isEqualTo(end);
        }

        @Test
        void 재시작하면_직전_오류_흔적이_지워진다() {
            // given
            LiveSession session = draft();
            session.markError("채팅방 생성 실패", Instant.parse("2026-09-10T10:00:00Z"));

            // when — ERROR는 재시도 가능한 상태다
            session.start(Instant.parse("2026-09-10T11:00:00Z"), "arn:chat");

            // then
            assertThat(session.getErrorDetail()).isNull();
            assertThat(session.getErrorOccurredAt()).isNull();
        }
    }

    @Test
    void 송출_실패는_상태와_사유를_함께_남긴다() {
        // given
        LiveSession session = draft();
        Instant now = Instant.parse("2026-09-10T11:00:00Z");

        // when — 예외만 던지면 판매자 화면이 무슨 일이 있었는지 보여줄 수 없다(PRD 6.3.4)
        session.markError("채팅방 생성 실패", now);

        // then
        assertThat(session.getStatus()).isEqualTo(LiveStatus.ERROR);
        assertThat(session.getErrorDetail()).isEqualTo("채팅방 생성 실패");
        assertThat(session.getErrorOccurredAt()).isEqualTo(now);
    }
}

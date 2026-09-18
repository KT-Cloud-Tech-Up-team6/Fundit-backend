package com.fundit.live.application.chat;

import com.fundit.common.error.BusinessException;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaEntity;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class VodChatQueryServiceUnitTest {

    private static final Instant STARTED = Instant.parse("2026-09-10T11:00:00Z");

    @Mock private ChatMessageJpaRepository chatMessageRepository;
    @Mock private LiveSessionJpaRepository sessionRepository;

    @InjectMocks private VodChatQueryService vodChatQueryService;

    private final UUID liveId = UUID.randomUUID();

    private void givenSession(Instant startedAt) {
        given(sessionRepository.findByPublicId(liveId)).willReturn(Optional.of(
                LiveSessionJpaEntity.builder().id(1L).publicId(liveId).channelId(1L)
                        .projectId(UUID.randomUUID()).status(LiveStatus.ENDED)
                        .actualStartAt(startedAt).likeCount(0).build()));
    }

    @Test
    void 초_구간을_방송_시작_기준_절대시각으로_바꿔_조회한다() {
        // given
        givenSession(STARTED);
        given(chatMessageRepository.findBySessionIdAndSentAtBetweenOrderBySentAtAsc(
                1L, STARTED.plusSeconds(60), STARTED.plusSeconds(120)))
                .willReturn(List.of(ChatMessageJpaEntity.builder().content("안녕").build()));

        // when
        var result = vodChatQueryService.findByRange(liveId, 60, 120);

        // then — 기준 시각을 함께 돌려줘야 호출부가 경과 초를 계산할 수 있다
        assertThat(result.broadcastStartedAt()).isEqualTo(STARTED);
        assertThat(result.messages()).hasSize(1);
    }

    @Test
    void 구간이_뒤집히면_400이다() {
        // when & then — 입력 검증은 서버에서 한다(security.md S2)
        assertThatThrownBy(() -> vodChatQueryService.findByRange(liveId, 120, 60))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> vodChatQueryService.findByRange(liveId, -1, 60))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 송출_기록이_없으면_초를_시각으로_바꿀_수_없어_409다() {
        // given
        givenSession(null);

        // when & then
        assertThatThrownBy(() -> vodChatQueryService.findByRange(liveId, 0, 60))
                .isInstanceOf(BusinessException.class);
    }
}

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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class VodChatQueryServiceUnitTest {

    private static final Instant STARTED = Instant.parse("2026-09-10T11:00:00Z");

    @Mock private ChatMessageJpaRepository chatMessageRepository;
    @Mock private LiveSessionJpaRepository sessionRepository;

    @InjectMocks private VodChatQueryService vodChatQueryService;

    private final UUID liveId = UUID.randomUUID();

    private void givenSession(Instant startedAt) {
        given(sessionRepository.findPublicByPublicId(liveId)).willReturn(Optional.of(
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



}

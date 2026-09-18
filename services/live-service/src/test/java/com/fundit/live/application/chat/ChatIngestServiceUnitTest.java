package com.fundit.live.application.chat;

import com.fundit.common.error.BusinessException;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChatIngestServiceUnitTest {

    @Mock private ChatMessageJpaRepository chatMessageRepository;
    @Mock private LiveSessionJpaRepository sessionRepository;

    @InjectMocks private ChatIngestService chatIngestService;

    @Test
    void 룸_ARN으로_세션을_찾아_적재한다() {
        // given — ARN이 세션 컬럼이라 조인 없이 단일 조회다
        given(sessionRepository.findByIvsChatRoomArn("arn:room")).willReturn(Optional.of(
                LiveSessionJpaEntity.builder().id(7L).publicId(UUID.randomUUID()).channelId(1L)
                        .projectId(UUID.randomUUID()).status(LiveStatus.LIVE).likeCount(0).build()));
        given(chatMessageRepository.insertIgnoringConflict(anyString(), anyLong(), any(), anyString(), any()))
                .willReturn(1);

        // when
        boolean stored = chatIngestService.ingest("arn:room", "msg-1", UUID.randomUUID(),
                "안녕하세요", Instant.parse("2026-09-10T11:00:00Z"));

        // then
        assertThat(stored).isTrue();
    }

    @Test
    void 재전송이면_false를_돌려주고_에러가_아니다() {
        // given — Firehose 재전송은 정상 흐름이다
        given(sessionRepository.findByIvsChatRoomArn("arn:room")).willReturn(Optional.of(
                LiveSessionJpaEntity.builder().id(7L).publicId(UUID.randomUUID()).channelId(1L)
                        .projectId(UUID.randomUUID()).status(LiveStatus.LIVE).likeCount(0).build()));
        given(chatMessageRepository.insertIgnoringConflict(anyString(), anyLong(), any(), anyString(), any()))
                .willReturn(0);

        // when & then
        assertThat(chatIngestService.ingest("arn:room", "msg-1", UUID.randomUUID(), "안녕",
                Instant.parse("2026-09-10T11:00:00Z"))).isFalse();
    }

    @Test
    void 모르는_채팅방이면_404다() {
        // given
        given(sessionRepository.findByIvsChatRoomArn("arn:unknown")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatIngestService.ingest("arn:unknown", "m", UUID.randomUUID(), "x",
                Instant.now())).isInstanceOf(BusinessException.class);
    }
}

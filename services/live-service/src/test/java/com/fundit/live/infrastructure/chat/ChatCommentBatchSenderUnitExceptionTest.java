package com.fundit.live.infrastructure.chat;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.application.ivs.IvsClient;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatCommentBatchSenderUnitExceptionTest {

    @Mock private LiveSessionJpaRepository sessionRepository;
    @Mock private ChatMessageJpaRepository chatMessageRepository;
    @Mock private AiClient aiClient;
    @Mock private IvsClient ivsClient;

    @InjectMocks private ChatCommentBatchSender sender;

    @Test
    void AI_호출이_실패해도_채팅_저장에는_영향이_없다() {
        // given — 방송·채팅은 AI 장애와 무관하게 정상이어야 한다(CLAUDE.md 원칙)
        LiveSessionJpaEntity session = LiveSessionJpaEntity.builder()
                .id(1L).publicId(UUID.randomUUID()).status(LiveStatus.LIVE)
                .actualStartAt(Instant.parse("2026-09-20T10:00:00Z")).build();
        ChatMessageJpaEntity message = ChatMessageJpaEntity.builder()
                .id(10L).ivsMessageId("m10").sessionId(1L).senderId(UUID.randomUUID())
                .content("질문").sentAt(Instant.parse("2026-09-20T10:05:00Z")).build();
        given(chatMessageRepository.findFirst50BySessionIdAndSentToAiAtIsNullOrderBySentAtAsc(1L))
                .willReturn(List.of(message));
        given(aiClient.submitComments(any(), any())).willThrow(new DependencyFailureException(new RuntimeException()));

        // when & then — 예외를 던지지 않고 조용히 다음 주기로 넘긴다
        sender.sendPendingFor(session);
        verify(chatMessageRepository, never()).markSentToAi(any(), any());
    }

    @Test
    void AI_답변_게시가_실패해도_전송_완료로_표시한다() {
        // given — 표시가 안 되면 같은 채팅이 AI에 다시 가서 답변이 두 번 만들어진다
        LiveSessionJpaEntity session = LiveSessionJpaEntity.builder()
                .id(1L).publicId(UUID.randomUUID()).status(LiveStatus.LIVE).ivsChatRoomArn("arn:room")
                .actualStartAt(Instant.parse("2026-09-20T10:00:00Z")).build();
        ChatMessageJpaEntity message = ChatMessageJpaEntity.builder()
                .id(10L).ivsMessageId("m10").sessionId(1L).senderId(UUID.randomUUID())
                .content("마감 언제예요?").sentAt(Instant.parse("2026-09-20T10:05:00Z")).build();
        given(chatMessageRepository.findFirst50BySessionIdAndSentToAiAtIsNullOrderBySentAtAsc(1L))
                .willReturn(List.of(message));
        given(aiClient.submitComments(any(), any())).willReturn(new AiClient.CommentBatchResult(
                List.of(new AiClient.AnsweredQuestion("q_10", "10", "마감 언제예요?", AiClient.HandledBy.PRODUCT,
                        "일정", 0L, new AiClient.GeneratedAnswer("9월 15일입니다", AiClient.Grounding.GROUNDED,
                        false, "KB"), List.of())),
                List.of(), List.of()));
        willThrow(new DependencyFailureException(new RuntimeException("ivs down")))
                .given(ivsClient).sendChatEvent(anyString(), anyString(), anyMap());

        // when
        sender.sendPendingFor(session);

        // then
        verify(chatMessageRepository).markSentToAi(eq(List.of(10L)), any());
    }
}

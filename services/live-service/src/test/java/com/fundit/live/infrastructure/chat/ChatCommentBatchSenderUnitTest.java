package com.fundit.live.infrastructure.chat;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaEntity;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaRepository;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatCommentBatchSenderUnitTest {

    @Mock private LiveSessionJpaRepository sessionRepository;
    @Mock private ChatMessageJpaRepository chatMessageRepository;
    @Mock private LiveQuestionSummaryJpaRepository summaryRepository;
    @Mock private AiClient aiClient;

    @InjectMocks private ChatCommentBatchSender sender;

    private LiveSessionJpaEntity session() {
        return LiveSessionJpaEntity.builder()
                .id(1L).publicId(UUID.randomUUID()).status(LiveStatus.LIVE)
                .actualStartAt(Instant.parse("2026-09-20T10:00:00Z")).build();
    }

    private ChatMessageJpaEntity message(long id) {
        return ChatMessageJpaEntity.builder()
                .id(id).ivsMessageId("m" + id).sessionId(1L).senderId(UUID.randomUUID())
                .content("질문").sentAt(Instant.parse("2026-09-20T10:05:00Z")).build();
    }

    @Test
    void 보낼_채팅이_없으면_AI를_부르지_않는다() {
        // given
        given(chatMessageRepository.findFirst50BySessionIdAndSentToAiAtIsNullOrderBySentAtAsc(1L))
                .willReturn(List.of());

        // when
        sender.sendPendingFor(session());

        // then
        verify(aiClient, never()).submitComments(any(), any());
    }

    @Test
    void 성공하면_전송_완료로_표시한다() {
        // given
        given(chatMessageRepository.findFirst50BySessionIdAndSentToAiAtIsNullOrderBySentAtAsc(1L))
                .willReturn(List.of(message(10L), message(11L)));
        given(aiClient.submitComments(any(), any())).willReturn(
                new AiClient.CommentBatchResult(List.of(),
                        List.of(new AiClient.IgnoredComment("10", "SMALLTALK"),
                                new AiClient.IgnoredComment("11", "SMALLTALK")),
                        List.of()));

        // when
        sender.sendPendingFor(session());

        // then
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatMessageRepository).markSentToAi(idsCaptor.capture(), any());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void errors에_실린_댓글은_전송_완료_표시에서_뺀다() {
        // given — 임의 답변으로 대체하지 않고 재시도 대상으로 남긴다
        given(chatMessageRepository.findFirst50BySessionIdAndSentToAiAtIsNullOrderBySentAtAsc(1L))
                .willReturn(List.of(message(10L), message(11L)));
        given(aiClient.submitComments(any(), any())).willReturn(
                new AiClient.CommentBatchResult(List.of(), List.of(),
                        List.of(new AiClient.CommentError("11", "EVIDENCE_UNAVAILABLE", "실패"))));

        // when
        sender.sendPendingFor(session());

        // then — 10만 완료 처리, 11은 다음 배치에서 재시도
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatMessageRepository).markSentToAi(idsCaptor.capture(), any());
        assertThat(idsCaptor.getValue()).containsExactly(10L);
    }

    @Test
    void AI_호출이_실패해도_채팅_저장에는_영향이_없다() {
        // given — 방송·채팅은 AI 장애와 무관하게 정상이어야 한다(CLAUDE.md 원칙)
        given(chatMessageRepository.findFirst50BySessionIdAndSentToAiAtIsNullOrderBySentAtAsc(1L))
                .willReturn(List.of(message(10L)));
        given(aiClient.submitComments(any(), any())).willThrow(new DependencyFailureException(new RuntimeException()));

        // when & then — 예외를 던지지 않고 조용히 다음 주기로 넘긴다
        sender.sendPendingFor(session());
        verify(chatMessageRepository, never()).markSentToAi(any(), any());
    }

    @Test
    void 근거있는_답변은_로컬_클러스터로_남긴다() {
        // given — PRODUCT+GROUNDED 응답은 즉시 답변된 것으로 기록한다
        given(chatMessageRepository.findFirst50BySessionIdAndSentToAiAtIsNullOrderBySentAtAsc(1L))
                .willReturn(List.of(message(10L)));
        given(aiClient.submitComments(any(), any())).willReturn(new AiClient.CommentBatchResult(
                List.of(new AiClient.AnsweredQuestion("q_0001", "10", "흡입력?",
                        AiClient.HandledBy.PRODUCT, "성능", 1000,
                        new AiClient.GeneratedAnswer("20000Pa입니다", AiClient.Grounding.GROUNDED, false, "kb_1"),
                        List.of())),
                List.of(), List.of()));
        given(summaryRepository.findBySessionIdAndAiQuestionId(1L, "q_0001")).willReturn(Optional.empty());

        // when
        sender.sendPendingFor(session());

        // then
        verify(summaryRepository).save(any());
    }
}

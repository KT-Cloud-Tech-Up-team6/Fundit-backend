package com.fundit.live.infrastructure.chat;

import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaEntity;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatCommentBatchSenderUnitTest {

    @Mock private LiveSessionJpaRepository sessionRepository;
    @Mock private ChatMessageJpaRepository chatMessageRepository;
    @Mock private AiClient aiClient;

    @InjectMocks private ChatCommentBatchSender sender;

    private LiveSessionJpaEntity session() {
        return LiveSessionJpaEntity.builder()
                .id(1L).publicId(UUID.randomUUID()).status(LiveStatus.LIVE)
                .actualStartAt(Instant.parse("2026-09-20T10:00:00Z")).build();
    }

    private ChatMessageJpaEntity message(long id) {
        return message(id, UUID.randomUUID(), "질문");
    }

    private ChatMessageJpaEntity message(long id, UUID senderId, String content) {
        return ChatMessageJpaEntity.builder()
                .id(id).ivsMessageId("m" + id).sessionId(1L).senderId(senderId)
                .content(content).sentAt(Instant.parse("2026-09-20T10:05:00Z")).build();
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
    void errors에_실린_댓글도_전송_완료로_표시해_큐가_막히지_않게_한다() {
        // given — 조회가 sent_at 오름차순이라 실패건을 남겨두면 그게 계속 앞자리를 차지한다
        given(chatMessageRepository.findFirst50BySessionIdAndSentToAiAtIsNullOrderBySentAtAsc(1L))
                .willReturn(List.of(message(10L), message(11L)));
        given(aiClient.submitComments(any(), any())).willReturn(
                new AiClient.CommentBatchResult(List.of(),
                        List.of(new AiClient.IgnoredComment("10", "SMALLTALK")),
                        List.of(new AiClient.CommentError("11", "EVIDENCE_UNAVAILABLE", "실패"))));

        // when
        sender.sendPendingFor(session());

        // then — 실패건도 완료로 찍어 다음 배치가 뒤 채팅으로 넘어간다
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatMessageRepository).markSentToAi(idsCaptor.capture(), any());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void 같은_사람이_같은_말을_반복하면_AI로_넘기지_않는다() {
        // given — 채팅 1건이 곧 LLM 호출 1건이라 도배는 그대로 비용이 된다
        UUID spammer = UUID.randomUUID();
        given(chatMessageRepository.findFirst50BySessionIdAndSentToAiAtIsNullOrderBySentAtAsc(1L))
                .willReturn(List.of(message(10L, spammer, "사주세요"), message(11L, spammer, "사주세요"),
                        message(12L, spammer, "언제 끝나요?")));
        given(aiClient.submitComments(any(), any())).willReturn(new AiClient.CommentBatchResult(
                List.of(), List.of(new AiClient.IgnoredComment("10", "SMALLTALK"),
                        new AiClient.IgnoredComment("12", "SMALLTALK")), List.of()));

        // when
        sender.sendPendingFor(session());

        // then — 중복분(11)은 AI로 안 가지만, 다시 골라지지 않게 완료 표시는 된다
        ArgumentCaptor<List<AiClient.CommentInput>> sentCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiClient).submitComments(any(), sentCaptor.capture());
        assertThat(sentCaptor.getValue()).extracting(AiClient.CommentInput::commentId)
                .containsExactly("10", "12");

        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatMessageRepository).markSentToAi(idsCaptor.capture(), any());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(10L, 11L, 12L);
    }

    @Test
    void 응답에_없는_댓글은_전송_완료로_표시하지_않는다() {
        // given — 11은 questions·ignored·errors 어디에도 없고, 99는 요청하지 않은 ID다
        given(chatMessageRepository.findFirst50BySessionIdAndSentToAiAtIsNullOrderBySentAtAsc(1L))
                .willReturn(List.of(message(10L), message(11L)));
        given(aiClient.submitComments(any(), any())).willReturn(new AiClient.CommentBatchResult(
                List.of(new AiClient.AnsweredQuestion("q_0001", "10", "흡입력?",
                        AiClient.HandledBy.PRODUCT, "성능", 1000,
                        new AiClient.GeneratedAnswer("20000Pa입니다", AiClient.Grounding.GROUNDED, false, "kb_1"),
                        List.of())),
                List.of(new AiClient.IgnoredComment("99", "SMALLTALK")), null));

        // when
        sender.sendPendingFor(session());

        // then — 10만 완료 처리, 11은 다음 배치에서 다시 보낸다
        ArgumentCaptor<List<Long>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatMessageRepository).markSentToAi(idsCaptor.capture(), any());
        assertThat(idsCaptor.getValue()).containsExactly(10L);
    }
}

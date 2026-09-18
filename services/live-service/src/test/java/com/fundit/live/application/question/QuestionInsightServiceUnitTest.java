package com.fundit.live.application.question;

import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaRepository;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class QuestionInsightServiceUnitTest {

    @Mock private LiveQuestionSummaryJpaRepository summaryRepository;
    @Mock private ChatMessageJpaRepository chatMessageRepository;
    @Mock private LiveSessionRepository sessionRepository;
    @Mock private AiClient aiClient;

    @InjectMocks private QuestionInsightService questionInsightService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    private LiveQuestionSummaryJpaEntity q(String topic, int count) {
        return LiveQuestionSummaryJpaEntity.builder()
                .id(1L).publicId(UUID.randomUUID()).sessionId(1L).topic(topic)
                .summaryText("질문").relatedQuestionCount(count).answered(false).build();
    }

    @Test
    void 관심사는_대표질문의_topic을_접어서_만든다() {
        // given — 같은 값을 테이블로 또 두지 않는다
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(summaryRepository.findBySessionIdOrderByRelatedQuestionCountDesc(1L))
                .willReturn(List.of(q("사이즈/색상", 12), q("사이즈/색상", 5), q("배송", 3)));
        given(aiClient.isReady(anyString())).willReturn(true);

        // when
        var insights = questionInsightService.insights(sellerId, liveId);

        // then
        assertThat(insights.topics()).containsEntry("사이즈/색상", 17).containsEntry("배송", 3);
        assertThat(insights.aiStatus()).isEqualTo("READY");
    }

    @Test
    void AI가_준비_안_됐으면_PREPARING으로_알린다() {
        // given — 빈 배열만 내려주면 "질문 0건"과 구분할 수 없다(PRD 6.4.4.4)
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(summaryRepository.findBySessionIdOrderByRelatedQuestionCountDesc(1L)).willReturn(List.of());
        given(aiClient.isReady(anyString())).willReturn(false);

        // when
        var insights = questionInsightService.insights(sellerId, liveId);

        // then
        assertThat(insights.aiStatus()).isEqualTo("PREPARING");
        assertThat(insights.representativeQuestions()).isEmpty();
    }

    @Test
    void 답변된_질문_모아보기는_인증_없이_조회된다() {
        // given
        given(sessionRepository.findOwnedAny(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(summaryRepository.findBySessionIdAndAnsweredTrueOrderByRelatedQuestionCountDesc(1L))
                .willReturn(List.of(q("사이즈/색상", 12)));

        // when
        var answered = questionInsightService.answeredQuestions(liveId);

        // then
        assertThat(answered).hasSize(1);
    }
}

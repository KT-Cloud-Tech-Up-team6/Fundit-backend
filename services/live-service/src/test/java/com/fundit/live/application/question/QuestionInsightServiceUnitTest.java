package com.fundit.live.application.question;

import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
class QuestionInsightServiceUnitTest {

    @Mock private LiveQuestionSummaryJpaRepository summaryRepository;
    @Mock private LiveSessionRepository sessionRepository;
    @Mock private AiClient aiClient;

    @InjectMocks private QuestionInsightService questionInsightService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    private LiveQuestionSummaryJpaEntity q(String qid, int count) {
        return LiveQuestionSummaryJpaEntity.builder()
                .id(1L).publicId(UUID.randomUUID()).sessionId(1L).aiQuestionId(qid)
                .summaryText("질문").relatedQuestionCount(count).answered(false).build();
    }

    @Test
    void faq는_AI_응답을_그대로_로컬에_반영한다() {
        // given — 집계는 AI가 한다. 여기서 다시 GROUP BY하지 않는다
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(summaryRepository.findBySessionIdAndAiQuestionId(1L, "fq_0002")).willReturn(Optional.empty());
        given(summaryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(aiClient.faq(anyString(), anyInt())).willReturn(new AiClient.FaqResult(180, List.of(
                new AiClient.FaqItem("fq_0002", "타이머 기능 돼요?", 4, "앱·원격제어",
                        AiClient.AnsweredBy.SELLER, Instant.now(), "네, 최대 12시간입니다.", true))));

        // when
        var result = questionInsightService.faq(sellerId, liveId, 10);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getSummaryText()).isEqualTo("타이머 기능 돼요?");
        assertThat(result.getFirst().getRelatedQuestionCount()).isEqualTo(4);
        assertThat(result.getFirst().isPromoted()).isTrue();
    }

    @Test
    void 이미_있는_클러스터는_갱신한다() {
        // given — 같은 qid가 다시 오면 새로 만들지 않고 값만 덮어쓴다
        LiveQuestionSummaryJpaEntity existing = q("fq_0002", 1);
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(summaryRepository.findBySessionIdAndAiQuestionId(1L, "fq_0002")).willReturn(Optional.of(existing));
        given(summaryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(aiClient.faq(anyString(), anyInt())).willReturn(new AiClient.FaqResult(180, List.of(
                new AiClient.FaqItem("fq_0002", "타이머 기능 돼요?", 5, "앱·원격제어",
                        AiClient.AnsweredBy.SELLER, Instant.now(), "네, 최대 12시간입니다.", true))));

        // when
        var result = questionInsightService.faq(sellerId, liveId, 10);

        // then — 새로 만들지 않고 같은 인스턴스가 갱신된다
        assertThat(result).containsExactly(existing);
        assertThat(existing.getRelatedQuestionCount()).isEqualTo(5);
    }

    @Test
    void unanswered는_pending과_answered를_각각_반영한다() {
        // given
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(summaryRepository.findBySessionIdAndAiQuestionId(1L, "fq_0002")).willReturn(Optional.empty());
        given(summaryRepository.findBySessionIdAndAiQuestionId(1L, "fq_0007")).willReturn(Optional.empty());
        given(summaryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(aiClient.unanswered(anyString(), anyInt())).willReturn(new AiClient.UnansweredList(
                List.of(new AiClient.UnansweredItem("fq_0002", "타이머 기능 돼요?", 3)),
                List.of(new AiClient.UnansweredItem("fq_0007", "판매자 답변 완료 건", 2))));

        // when
        var view = questionInsightService.unanswered(sellerId, liveId, 10);

        // then
        assertThat(view.pending()).hasSize(1);
        assertThat(view.answered()).hasSize(1);
    }

    @Test
    void 원본_채팅은_AI에_위임한다() {
        // given — 클러스터별 원본 댓글은 로컬에 두지 않고 AI가 갖고 있다
        UUID questionId = UUID.randomUUID();
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(summaryRepository.findByPublicIdAndSessionId(questionId, 1L))
                .willReturn(Optional.of(q("fq_0002", 4)));
        given(aiClient.faqComments(anyString(), org.mockito.ArgumentMatchers.eq("fq_0002")))
                .willReturn(new AiClient.FaqComments("fq_0002", 1,
                        List.of(new AiClient.FaqComment("c2", "예약 타이머 있어요?", 20000))));

        // when
        var comments = questionInsightService.originalMessages(sellerId, liveId, questionId);

        // then
        assertThat(comments).hasSize(1);
    }

    @Test
    void 답변된_질문_모아보기는_인증_없이_조회된다() {
        // given
        given(sessionRepository.findOwnedAny(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(summaryRepository.findBySessionIdAndAnsweredTrueOrderByRelatedQuestionCountDesc(1L))
                .willReturn(List.of(q("fq_0002", 12)));

        // when
        var answered = questionInsightService.answeredQuestions(liveId);

        // then
        assertThat(answered).hasSize(1);
    }
}

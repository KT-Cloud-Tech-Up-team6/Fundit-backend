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
                        AiClient.AnsweredBy.SELLER, Instant.now(), "네, 최대 12시간입니다.", true,
                        AiClient.HandledBy.PRODUCT))));

        // when
        var result = questionInsightService.faq(sellerId, liveId, 10);

        // then
        assertThat(result.summaries()).hasSize(1);
        assertThat(result.summaries().getFirst().getSummaryText()).isEqualTo("타이머 기능 돼요?");
        assertThat(result.summaries().getFirst().getRelatedQuestionCount()).isEqualTo(4);
        assertThat(result.summaries().getFirst().isPromoted()).isTrue();
        // handled_by가 FaqItem DTO에 빠져 있어 저장이 안 되던 것(AI팀 회신, 2026-09-23)
        assertThat(result.summaries().getFirst().getHandledBy()).isEqualTo(AiClient.HandledBy.PRODUCT);
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
                        AiClient.AnsweredBy.SELLER, Instant.now(), "네, 최대 12시간입니다.", true,
                        AiClient.HandledBy.PRODUCT))));

        // when
        var result = questionInsightService.faq(sellerId, liveId, 10);

        // then — 새로 만들지 않고 같은 인스턴스가 갱신된다
        assertThat(result.summaries()).containsExactly(existing);
        assertThat(existing.getRelatedQuestionCount()).isEqualTo(5);
    }

    @Test
    void unanswered의_answered는_기존_행만_돌려주고_답변을_보존한다() {
        // given — fq_0007은 판매자가 답변해 둔 행, fq_0009는 로컬에 없는 행
        LiveQuestionSummaryJpaEntity sellerAnswered = q("fq_0007", 2);
        sellerAnswered.recordAnswer("네, 됩니다.", Instant.now());
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(summaryRepository.findBySessionIdAndAiQuestionId(1L, "fq_0002")).willReturn(Optional.empty());
        given(summaryRepository.findBySessionIdAndAiQuestionId(1L, "fq_0007")).willReturn(Optional.of(sellerAnswered));
        given(summaryRepository.findBySessionIdAndAiQuestionId(1L, "fq_0009")).willReturn(Optional.empty());
        given(summaryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(aiClient.unanswered(anyString(), anyInt())).willReturn(new AiClient.UnansweredList(
                List.of(new AiClient.UnansweredItem("fq_0002", "타이머 기능 돼요?", 3)),
                List.of(new AiClient.UnansweredItem("fq_0007", "판매자 답변 완료 건", 2),
                        new AiClient.UnansweredItem("fq_0009", "모르는 건", 1))));

        // when
        var view = questionInsightService.unanswered(sellerId, liveId, 10);

        // then — 없는 행으로 답변 정보 없는 "완료" 행을 만들지 않는다
        assertThat(view.pending()).hasSize(1);
        assertThat(view.answered()).containsExactly(sellerAnswered);
        assertThat(sellerAnswered.getAnsweredBy()).isEqualTo(AiClient.AnsweredBy.SELLER);
    }

    @Test
    void unanswered_조회가_승격_표시를_지우지_않는다() {
        // given — 이미 TOP3로 승격된 행. UnansweredItem에는 promoted가 없어 기존 값을 살려야 한다
        LiveQuestionSummaryJpaEntity promoted = q("fq_0002", 3);
        promoted.applyFromAi(new AiClient.FaqItem("fq_0002", "타이머 기능 돼요?", 3, "앱·원격제어",
                AiClient.AnsweredBy.NONE, null, null, true, AiClient.HandledBy.PRODUCT));
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(summaryRepository.findBySessionIdAndAiQuestionId(1L, "fq_0002")).willReturn(Optional.of(promoted));
        given(summaryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(aiClient.unanswered(anyString(), anyInt())).willReturn(new AiClient.UnansweredList(
                List.of(new AiClient.UnansweredItem("fq_0002", "타이머 기능 돼요?", 4)), List.of()));

        // when
        questionInsightService.unanswered(sellerId, liveId, 10);

        // then
        assertThat(promoted.isPromoted()).isTrue();
    }

    @Test
    void AI가_NONE으로_와도_판매자_답변을_지우지_않는다() {
        // given — 판매자가 먼저 답변했고, AI 쪽 집계는 아직 반영 전이라 NONE으로 온다
        LiveQuestionSummaryJpaEntity existing = q("fq_0002", 1);
        existing.recordAnswer("네, 됩니다.", Instant.now());
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(summaryRepository.findBySessionIdAndAiQuestionId(1L, "fq_0002")).willReturn(Optional.of(existing));
        given(summaryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(aiClient.faq(anyString(), anyInt())).willReturn(new AiClient.FaqResult(180, List.of(
                new AiClient.FaqItem("fq_0002", "타이머 기능 돼요?", 3, "앱·원격제어",
                        AiClient.AnsweredBy.NONE, null, null, false, AiClient.HandledBy.UNANSWERABLE))));

        // when
        questionInsightService.faq(sellerId, liveId, 10);

        // then
        assertThat(existing.isAnswered()).isTrue();
        assertThat(existing.getAnsweredBy()).isEqualTo(AiClient.AnsweredBy.SELLER);
        assertThat(existing.getAnswerText()).isEqualTo("네, 됩니다.");
        assertThat(existing.getRelatedQuestionCount()).isEqualTo(3);
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
        given(sessionRepository.findPublic(liveId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(summaryRepository.findBySessionIdAndAnsweredTrueOrderByRelatedQuestionCountDesc(1L))
                .willReturn(List.of(q("fq_0002", 12)));

        // when
        var answered = questionInsightService.answeredQuestions(liveId);

        // then
        assertThat(answered).hasSize(1);
    }
}

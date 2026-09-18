package com.fundit.live.application.question;

import com.fundit.common.error.BusinessException;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AiAnswerServiceUnitTest {

    @Mock private LiveQuestionSummaryJpaRepository summaryRepository;
    @Mock private LiveSessionRepository sessionRepository;
    @Mock private AiClient aiClient;

    @InjectMocks private AiAnswerService aiAnswerService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();

    private LiveQuestionSummaryJpaEntity summary() {
        return LiveQuestionSummaryJpaEntity.builder()
                .id(5L).publicId(questionId).sessionId(1L)
                .summaryText("사이즈가 어떻게 되나요?").relatedQuestionCount(12).answered(false).build();
    }

    private void givenOwnedAndSummary(LiveQuestionSummaryJpaEntity s) {
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
        given(summaryRepository.findByPublicId(questionId)).willReturn(Optional.of(s));
    }

    @Test
    void GENERATE는_초안만_만들고_답변으로_기록하지_않는다() {
        // given — 자동 게시가 아니다. 판매자 승인 후 전송이 협의 결정이다.
        LiveQuestionSummaryJpaEntity s = summary();
        givenOwnedAndSummary(s);
        given(aiClient.generateAnswer(anyString(), anyString(), any()))
                .willReturn(new AiClient.AnswerDraft("500ml/700ml 두 가지입니다.", true));

        // when
        AiClient.AnswerDraft draft = aiAnswerService.generate(sellerId, liveId, questionId, List.of("상세페이지"));

        // then
        assertThat(draft.draftAnswer()).isNotBlank();
        assertThat(s.isAnswered()).isFalse();
        assertThat(s.getAnswerText()).isNull();
    }

    @Test
    void 근거가_없으면_grounded_false지만_에러가_아니다() {
        // given — 503으로 올리면 화면이 Empty State를 그릴 수 없다(PRD 6.4.4.5)
        givenOwnedAndSummary(summary());
        given(aiClient.generateAnswer(anyString(), anyString(), any()))
                .willReturn(new AiClient.AnswerDraft(null, false));

        // when
        AiClient.AnswerDraft draft = aiAnswerService.generate(sellerId, liveId, questionId, List.of());

        // then
        assertThat(draft.grounded()).isFalse();
    }

    @Test
    void SEND해야_답변이_저장된다() {
        // given — 채팅 스트림은 지나가면 끝이라 저장하지 않으면 Q&A 버튼이 보여줄 게 없다
        LiveQuestionSummaryJpaEntity s = summary();
        givenOwnedAndSummary(s);

        // when
        aiAnswerService.send(sellerId, liveId, questionId, "500ml/700ml 두 가지입니다.");

        // then
        assertThat(s.isAnswered()).isTrue();
        assertThat(s.getAnswerText()).isEqualTo("500ml/700ml 두 가지입니다.");
        assertThat(s.getAnsweredAt()).isNotNull();
    }

    @Test
    void 빈_답변은_보낼_수_없다() {
        // when & then
        assertThatThrownBy(() -> aiAnswerService.send(sellerId, liveId, questionId, "  "))
                .isInstanceOf(BusinessException.class);
    }
}

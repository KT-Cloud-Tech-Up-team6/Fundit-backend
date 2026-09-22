package com.fundit.live.application.question;

import com.fundit.common.error.BusinessException;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class QuestionInsightServiceUnitExceptionTest {

    @Mock private LiveQuestionSummaryJpaRepository summaryRepository;
    @Mock private LiveSessionRepository sessionRepository;
    @Mock private AiClient aiClient;

    @InjectMocks private QuestionInsightService questionInsightService;

    private final UUID liveId = UUID.randomUUID();

    @Test
    void 설정_중인_방송의_답변_모아보기는_404다() {
        // given — 소비자에게 열린 경로라 DRAFT는 존재 자체를 숨긴다(S10)
        given(sessionRepository.findPublic(liveId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> questionInsightService.answeredQuestions(liveId))
                .isInstanceOf(BusinessException.class);
    }
}

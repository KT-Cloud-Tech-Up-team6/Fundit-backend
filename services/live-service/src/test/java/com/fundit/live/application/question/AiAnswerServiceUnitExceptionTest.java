package com.fundit.live.application.question;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AiAnswerServiceUnitExceptionTest {

    @Mock private LiveQuestionSummaryJpaRepository summaryRepository;
    @Mock private LiveSessionRepository sessionRepository;
    @Mock private AiClient aiClient;

    @InjectMocks private AiAnswerService aiAnswerService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();

    private void givenOwned() {
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.builder().id(1L).publicId(liveId).build()));
    }

    @Test
    void 빈_답변은_보낼_수_없다() {
        // when & then
        assertThatThrownBy(() -> aiAnswerService.send(sellerId, liveId, questionId, "  "))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }

    @Test
    void 다른_LIVE의_질문에는_답변을_기록할_수_없다() {
        // given — 자기 liveId에 남의 questionId를 붙이는 IDOR를 막는다. 소속을 조회에 묶었다(S4)
        givenOwned();
        given(summaryRepository.findByPublicIdAndSessionId(questionId, 1L)).willReturn(Optional.empty());

        // when & then — 존재를 알리지 않으려 404다(S10)
        assertThatThrownBy(() -> aiAnswerService.send(sellerId, liveId, questionId, "답변"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    void 다른_LIVE의_질문으로는_초안도_만들_수_없다() {
        // given
        givenOwned();
        given(summaryRepository.findByPublicIdAndSessionId(questionId, 1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> aiAnswerService.generate(sellerId, liveId, questionId, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }
}

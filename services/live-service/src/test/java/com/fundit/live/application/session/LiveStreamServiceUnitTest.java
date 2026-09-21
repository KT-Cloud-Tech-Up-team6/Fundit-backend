package com.fundit.live.application.session;

import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.event.LiveEventOutboxJpaEntity;
import com.fundit.live.infrastructure.persistence.event.LiveEventOutboxJpaRepository;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;
import com.fundit.live.domain.session.LiveStatus;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveStreamServiceUnitTest {

    @Mock private LiveSessionRepository sessionRepository;
    @Mock private LiveEventOutboxJpaRepository outboxRepository;
    @Mock private IvsClient ivsClient;

    @InjectMocks private LiveStreamService liveStreamService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    @Test
    void 시작하면_LIVE로_전이한다() {
        // given
        LiveSession session = LiveSession.create(1L, UUID.randomUUID());
        given(sessionRepository.findOwnedForUpdate(liveId, sellerId)).willReturn(Optional.of(session));
        given(sessionRepository.save(any(LiveSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        LiveSession started = liveStreamService.start(sellerId, liveId);

        // then
        assertThat(started.getStatus()).isEqualTo(LiveStatus.LIVE);
        assertThat(started.getActualStartAt()).isNotNull();
    }

    @Test
    void 종료하면_ENDED로_전이한다() {
        // given
        LiveSession session = LiveSession.create(1L, UUID.randomUUID());
        session.start(Instant.parse("2026-09-10T11:00:00Z"), "arn:chat");
        given(sessionRepository.findOwnedForUpdate(liveId, sellerId)).willReturn(Optional.of(session));
        given(sessionRepository.save(any(LiveSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        LiveSession ended = liveStreamService.end(sellerId, liveId);

        // then
        assertThat(ended.getStatus()).isEqualTo(LiveStatus.ENDED);
        assertThat(ended.getActualEndAt()).isNotNull();
    }

    @Test
    void 질문요약_이벤트_payload는_summaries_배열을_담는다() {
        // given — LiveDomainApiSpec.md "질문요약 발행" 절 필드명(questionSummaryId/summaryText/
        // questionCount)과 맞춰야 project-service 컨슈머가 파싱할 수 있다
        LiveSession session = LiveSession.create(1L, UUID.randomUUID())
                .toBuilder().id(10L).publicId(liveId).build();
        LiveQuestionSummaryJpaEntity summary = LiveQuestionSummaryJpaEntity.builder()
                .id(1L).publicId(UUID.randomUUID()).sessionId(10L)
                .summaryText("배송은 얼마나 걸리나요?").relatedQuestionCount(12).answered(true).build();

        // when
        liveStreamService.appendQuestionsSummarizedOutbox(session, List.of(summary));

        // then
        ArgumentCaptor<LiveEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(LiveEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        LiveEventOutboxJpaEntity saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(LiveEventOutboxJpaEntity.TYPE_QUESTIONS_SUMMARIZED);
        assertThat(saved.getPayload())
                .contains("\"summaries\"")
                .contains(summary.getPublicId().toString())
                .contains("\"summaryText\":\"배송은 얼마나 걸리나요?\"")
                .contains("\"questionCount\":12");
    }
}

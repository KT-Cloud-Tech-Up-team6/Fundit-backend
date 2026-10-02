package com.fundit.live.application.session;

import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.application.project.ProjectContextClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveStreamServiceUnitTest {

    @Mock private LiveSessionRepository sessionRepository;
    @Mock private LiveEventOutboxJpaRepository outboxRepository;
    @Mock private IvsClient ivsClient;
    @Mock private ProjectContextClient projectContextClient;
    @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Mock private LiveChannelJpaRepository channelRepository;

    @InjectMocks private LiveStreamService liveStreamService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    @Test
    void 송출_정보는_저장된_참조로_키_값을_꺼내_돌려준다() {
        // given — DB엔 ARN(참조)만 있고 값은 요청 시점에 IVS에서 꺼낸다(S9)
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.create(1L, UUID.randomUUID())));
        given(channelRepository.findBySellerId(sellerId)).willReturn(Optional.of(LiveChannelJpaEntity.builder()
                .sellerId(sellerId).ivsChannelArn("arn:channel").ivsIngestEndpoint("rtmps://ingest:443/app/")
                .ivsPlaybackUrl("https://play").ivsStreamKeyRef("arn:stream-key").active(true).build()));
        given(ivsClient.getStreamKeyValue("arn:stream-key")).willReturn("sk_secret");

        // when
        LiveStreamService.StreamInfo info = liveStreamService.streamInfo(sellerId, liveId);

        // then
        assertThat(info.ingestEndpoint()).isEqualTo("rtmps://ingest:443/app/");
        assertThat(info.streamKey()).isEqualTo("sk_secret");
    }

    @Test
    void 송출_상태는_LIVE면_채널로_IVS에_묻는다() {
        // given
        LiveSession session = LiveSession.create(1L, UUID.randomUUID());
        session.start(Instant.parse("2026-09-10T11:00:00Z"), "arn:chat");
        given(sessionRepository.findOwned(liveId, sellerId)).willReturn(Optional.of(session));
        given(channelRepository.findBySellerId(sellerId)).willReturn(Optional.of(LiveChannelJpaEntity.builder()
                .sellerId(sellerId).ivsChannelArn("arn:channel").active(true).build()));
        IvsClient.StreamStatus live = new IvsClient.StreamStatus("LIVE", "HEALTHY", 7, Instant.parse("2026-09-10T11:00:00Z"));
        given(ivsClient.getStreamStatus("arn:channel")).willReturn(live);

        // when
        IvsClient.StreamStatus status = liveStreamService.streamStatus(sellerId, liveId);

        // then
        assertThat(status).isEqualTo(live);
    }

    @Test
    void 송출_상태는_LIVE가_아니면_IVS를_부르지_않고_OFFLINE이다() {
        // given — 시작 전 화면이 폴링을 계속해도 IVS 호출 한도를 쓰지 않는다
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.create(1L, UUID.randomUUID())));

        // when
        IvsClient.StreamStatus status = liveStreamService.streamStatus(sellerId, liveId);

        // then
        assertThat(status).isEqualTo(IvsClient.StreamStatus.OFFLINE);
        verify(ivsClient, never()).getStreamStatus(anyString());
    }

    @Test
    void 색인_완료_표시는_새_트랜잭션에서_저장한다() {
        // given — afterCommit 안의 쓰기는 이미 커밋된 트랜잭션에 합류해 조용히 버려진다.
        // REQUIRES_NEW가 빠지면 ai_prepared_at이 영영 안 남아 화면이 계속 PREPARING이 된다.
        LiveSession session = LiveSession.create(1L, UUID.randomUUID());

        // when
        liveStreamService.markAiPrepared(session);

        // then
        ArgumentCaptor<org.springframework.transaction.TransactionDefinition> defCaptor =
                ArgumentCaptor.forClass(org.springframework.transaction.TransactionDefinition.class);
        verify(transactionManager).getTransaction(defCaptor.capture());
        assertThat(defCaptor.getValue().getPropagationBehavior())
                .isEqualTo(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(session.getAiPreparedAt()).isNotNull();
        verify(sessionRepository).save(session);
    }

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
    void 시작하면_아웃박스에_프로젝트명을_같이_싣는다() {
        // given — notification-service가 "「프로젝트명」 LIVE가 시작됐어요" 문구를 만들 재료다
        LiveSession session = LiveSession.create(1L, UUID.randomUUID());
        given(sessionRepository.findOwnedForUpdate(liveId, sellerId)).willReturn(Optional.of(session));
        given(sessionRepository.save(any(LiveSession.class))).willAnswer(inv -> inv.getArgument(0));
        given(projectContextClient.find(session.getProjectId())).willReturn(Optional.of(
                new ProjectContextClient.ProjectContext("무선 이어폰", "가전", "음향", List.of(), 0, null, null)));

        // when
        liveStreamService.start(sellerId, liveId);

        // then
        ArgumentCaptor<LiveEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(LiveEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("\"projectTitle\":\"무선 이어폰\"");
    }

    @Test
    void 프로젝트_조회가_실패해도_방송_시작은_성공한다() {
        // given — 알림 문구가 일반 문구로 대체될 뿐, 방송 시작 자체를 막으면 안 된다
        LiveSession session = LiveSession.create(1L, UUID.randomUUID());
        given(sessionRepository.findOwnedForUpdate(liveId, sellerId)).willReturn(Optional.of(session));
        given(sessionRepository.save(any(LiveSession.class))).willAnswer(inv -> inv.getArgument(0));
        given(projectContextClient.find(session.getProjectId()))
                .willThrow(new com.fundit.common.error.DependencyFailureException(new RuntimeException("boom")));

        // when
        LiveSession started = liveStreamService.start(sellerId, liveId);

        // then
        assertThat(started.getStatus()).isEqualTo(LiveStatus.LIVE);
        ArgumentCaptor<LiveEventOutboxJpaEntity> captor = ArgumentCaptor.forClass(LiveEventOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("\"projectTitle\":null");
    }

    @Test
    void 종료하면_ENDED로_전이하고_커밋_후에_IVS_송출을_끊는다() {
        // given — 송출을 안 끊으면 OBS를 켠 채 다음 방송이 같은 녹화로 이어진다(#232).
        // 커밋 전에 끊으면 종료가 롤백돼도 송출은 이미 끊겨 있다(PR #237 리뷰).
        LiveSession session = LiveSession.create(1L, UUID.randomUUID());
        session.start(Instant.parse("2026-09-10T11:00:00Z"), "arn:chat");
        given(sessionRepository.findOwnedForUpdate(liveId, sellerId)).willReturn(Optional.of(session));
        given(sessionRepository.save(any(LiveSession.class))).willAnswer(inv -> inv.getArgument(0));
        given(channelRepository.findById(1L)).willReturn(Optional.of(LiveChannelJpaEntity.builder()
                .sellerId(sellerId).ivsChannelArn("arn:channel").active(true).build()));
        TransactionSynchronizationManager.initSynchronization();
        try {
            // when
            LiveSession ended = liveStreamService.end(sellerId, liveId);

            // then — 커밋 전에는 끊지 않고, 커밋 후 첫 동기화(송출 중지)에서 끊는다
            assertThat(ended.getStatus()).isEqualTo(LiveStatus.ENDED);
            assertThat(ended.getActualEndAt()).isNotNull();
            verify(ivsClient, never()).stopStream(anyString());
            TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
            verify(ivsClient).stopStream("arn:channel");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
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

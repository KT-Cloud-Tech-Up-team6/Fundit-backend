package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.application.ai.AiProductContextAssembler;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.application.project.ProjectContextClient;
import com.fundit.live.application.question.QuestionInsightService;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.event.LiveEventTransport.QuestionsSummarizedEvent;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.event.LiveEventOutboxJpaEntity;
import com.fundit.live.infrastructure.persistence.event.LiveEventOutboxJpaRepository;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LIVE 시작/종료(요구사항정의서 6.3.4).
 *
 * <p>IVS 호출이 실패하면 상태를 {@code ERROR}로 남기고 사유를 적은 뒤 예외를 던진다 —
 * 그냥 던지고 말면 판매자 화면이 "무슨 일이 있었는지"를 보여줄 수 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveStreamService {

    private final LiveSessionRepository sessionRepository;
    private final LiveEventOutboxJpaRepository outboxRepository;
    private final IvsClient ivsClient;
    private final AiClient aiClient;
    private final AiProductContextAssembler productContextAssembler;
    private final QuestionInsightService questionInsightService;
    private final ProjectContextClient projectContextClient;
    private final PlatformTransactionManager transactionManager;
    private final LiveChannelJpaRepository channelRepository;

    private static final int FINAL_SUMMARY_TOP_N = 100;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /**
     * {@code noRollbackFor}가 필요한 이유: IVS 실패 시 ERROR 상태를 저장하고
     * {@link DependencyFailureException}을 던지는데, 그건 RuntimeException이라 기본 롤백 대상이다.
     * 그대로 두면 <b>저장한 ERROR가 커밋되지 않아</b> 판매자 화면이 실패 사유를 영영 못 본다
     * (요구사항정의서 6.3.4).
     */
    @Transactional(noRollbackFor = DependencyFailureException.class)
    public LiveSession start(UUID sellerId, UUID liveId) {
        LiveSession session = loadOwned(sellerId, liveId);
        // IVS를 부르기 전에 상태를 본다. 뒤에서 검증하면 이미 끝난 방송에 시작 요청이 들어왔을 때
        // 실패 시 ENDED가 ERROR로 덮이고, 성공 시 채팅방만 만들어진 채 409가 나 자원이 샌다.
        session.requireStartable();

        Instant now = Instant.now();
        String chatRoomArn;
        try {
            chatRoomArn = ivsClient.createChatRoom(liveId.toString());
        } catch (RuntimeException e) {
            session.markError("채팅방 생성 실패: " + e.getClass().getSimpleName(), now);
            sessionRepository.save(session);
            throw new DependencyFailureException(e);
        }
        session.start(now, chatRoomArn);
        LiveSession saved = sessionRepository.save(session);
        // 도메인 변경과 같은 트랜잭션에 적재한다 — 방송은 시작됐는데 이벤트만 사라지는 경우가 없다.
        // liveId뿐 아니라 프로젝트명도 여기서 같이 실어 보낸다 — notification-service가
        // "「프로젝트명」 LIVE가 시작됐어요" 문구를 만들려면 이 값이 필요한데, 그쪽은
        // 다른 서비스 데이터를 되묻지 않는다는 원칙이 있어(발행 측이 완성된 문장을 보낸다) 여기서 채운다.
        appendOutbox(saved, LiveEventOutboxJpaEntity.TYPE_LIVE_STARTED, now,
                fetchProjectTitleOrNull(saved.getProjectId()));
        schedulePrepare(saved);
        return saved;
    }

    /**
     * AI 상품정보 색인({@code prepare})은 트랜잭션 커밋 후에 호출한다 — AI가 느리거나 실패해도
     * 방송 시작 자체가 지연되거나 롤백되면 안 된다(요구사항정의서 6.4.4.2와 같은 원칙).
     *
     * <p>성공하면 {@code ai_prepared_at}을 남겨 판매자 화면의 {@code aiStatus} 판단 근거로 쓴다.
     * 그 저장은 <b>{@code REQUIRES_NEW}로 새 트랜잭션을 열어야 한다</b> — {@code afterCommit} 안의
     * 쓰기는 이미 커밋된 트랜잭션에 합류해 조용히 버려진다({@code scheduleQuestionsSummarized}와 같은 이유).
     */
    private void schedulePrepare(LiveSession session) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    productContextAssembler.assemble(session).ifPresentOrElse(
                            request -> {
                                aiClient.prepare(session.getPublicId().toString(), request);
                                markAiPrepared(session);
                            },
                            () -> log.warn("프로젝트 조회 실패로 AI 상품정보 색인을 건너뛴다, liveId={} projectId={}",
                                    session.getPublicId(), session.getProjectId()));
                } catch (RuntimeException e) {
                    // ponytail: 실패 시 재시도 없이 로그만 남긴다. 운영에서 누락이 보이면 재시도 작업 테이블로 옮긴다.
                    log.warn("AI prepare 실패, liveId={}", session.getPublicId(), e);
                }
            }
        });
    }

    /** package-private — afterCommit 바깥에서 이 저장만 따로 검증하기 위해 접근 제한을 풀어둔다. */
    void markAiPrepared(LiveSession session) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            session.markAiPrepared(Instant.now());
            sessionRepository.save(session);
        });
    }

    @Transactional
    public LiveSession end(UUID sellerId, UUID liveId) {
        LiveSession session = loadOwned(sellerId, liveId);
        Instant now = Instant.now();
        session.end(now);
        LiveSession saved = sessionRepository.save(session);
        // live.ended.v1은 방송 후 자산(질문요약·하이라이트) 두 종류의 유일한 트리거다.
        // 유실되면 방송이 이미 끝나서 재생성할 방법이 없다.
        appendOutbox(saved, LiveEventOutboxJpaEntity.TYPE_LIVE_ENDED, now, null);
        scheduleQuestionsSummarized(sellerId, saved);
        return saved;
    }

    /**
     * 방송 종료 시점에 AI 최종 집계를 한 번 더 반영({@code QuestionInsightService.faq}가 upsert까지
     * 끝낸다) 한 뒤 그 결과로 {@code live.questions-summarized.v1}을 발행한다 — project-service의
     * LIVE 검증 탭이 이 이벤트로만 채워진다(`LiveDomainApiSpec.md` "질문요약 발행" 절, 담당자 협의
     * 확정). AI 실패로 방송 종료 자체가 막히면 안 되므로 트랜잭션 커밋 후에 호출한다.
     *
     * <p>{@code afterCommit} 안의 쓰기는 이미 커밋된 트랜잭션에 합류해 버려 커밋되지 않는다
     * ({@code TransactionSynchronization#afterCommit} javadoc) — 그래서 {@code REQUIRES_NEW}로
     * 새 트랜잭션을 열고, 요약 upsert와 아웃박스 적재를 그 안에서 함께 커밋한다.
     */
    private void scheduleQuestionsSummarized(UUID sellerId, LiveSession session) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // ponytail: AI 장애면 발행을 포기하고 로그만 남긴다. 유실이 실제로 문제 되면 재시도 작업 테이블로 옮긴다.
                try {
                    TransactionTemplate tx = new TransactionTemplate(transactionManager);
                    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    tx.executeWithoutResult(status -> appendQuestionsSummarizedOutbox(session,
                            questionInsightService.faq(sellerId, session.getPublicId(), FINAL_SUMMARY_TOP_N)
                                    .summaries()));
                } catch (RuntimeException e) {
                    log.warn("AI 질문요약 발행 실패, liveId={}", session.getPublicId(), e);
                }
            }
        });
    }

    /** package-private — payload 조립 로직만 단위 테스트에서 직접 검증하기 위해 접근 제한을 풀어둔다. */
    void appendQuestionsSummarizedOutbox(LiveSession session, List<LiveQuestionSummaryJpaEntity> summaries) {
        List<QuestionsSummarizedEvent.SummaryItem> items = summaries.stream()
                .map(s -> new QuestionsSummarizedEvent.SummaryItem(
                        s.getPublicId().toString(), s.getSummaryText(), s.getRelatedQuestionCount()))
                .toList();
        String payload = jsonMapper.writeValueAsString(Map.of(
                "liveId", session.getPublicId().toString(),
                "projectId", session.getProjectId().toString(),
                "summaries", items));
        outboxRepository.save(LiveEventOutboxJpaEntity.builder()
                .eventType(LiveEventOutboxJpaEntity.TYPE_QUESTIONS_SUMMARIZED)
                .liveSessionId(session.getId())
                .payload(payload)
                .build());
    }

    /**
     * project-service 조회 실패가 방송 시작을 막으면 안 된다 — 문구가 일반 문구로 대체될 뿐이다.
     * {@code start()}가 이미 IVS 호출로 외부 의존 하나를 동기로 안고 있어, 같은 자리에 project-service
     * 조회를 하나 더 두는 게 새 선례는 아니다(둘 다 타임아웃이 짧게 잡혀 있다).
     */
    private String fetchProjectTitleOrNull(UUID projectId) {
        try {
            return projectContextClient.find(projectId).map(ProjectContextClient.ProjectContext::title).orElse(null);
        } catch (RuntimeException e) {
            log.warn("프로젝트 제목 조회 실패, 알림 문구를 일반 문구로 대체, projectId={}", projectId, e);
            return null;
        }
    }

    private void appendOutbox(LiveSession session, String eventType, Instant occurredAt, String projectTitle) {
        String payload = jsonMapper.writeValueAsString(new OutboxPayload(
                session.getPublicId().toString(), session.getProjectId().toString(),
                occurredAt.toString(), projectTitle));
        outboxRepository.save(LiveEventOutboxJpaEntity.builder()
                .eventType(eventType)
                .liveSessionId(session.getId())
                .payload(payload)
                .build());
    }

    /**
     * LIVE_STARTED/LIVE_ENDED 공통 페이로드. {@code projectTitle}은 LIVE_STARTED에서만 채워진다
     * (제목에 따옴표가 섞일 수 있어 문자열 템플릿 대신 매퍼로 이스케이프한다).
     */
    private record OutboxPayload(String liveId, String projectId, String occurredAt, String projectTitle) {
    }

    /**
     * 판매자 송출 정보(OBS에 넣을 ingest 주소·스트림 키). 키는 DB에 참조(ARN)만 있고 값은
     * 요청 시점에 IVS에서 꺼낸다(S9). 상태를 안 바꾸는 조회라 락 없는 {@code findOwned}로
     * 소유권만 본다.
     *
     * <p>트랜잭션을 걸지 않는다 — 걸면 IVS 응답을 기다리는 동안 DB 커넥션을 잡고 있다. 두 조회는
     * 각자 짧은 트랜잭션으로 끝나고 읽는 값도 지연 로딩 없는 컬럼뿐이다.
     */
    public StreamInfo streamInfo(UUID sellerId, UUID liveId) {
        sessionRepository.findOwned(liveId, sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        LiveChannelJpaEntity channel = channelRepository.findBySellerId(sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return new StreamInfo(channel.getIvsIngestEndpoint(),
                ivsClient.getStreamKeyValue(channel.getIvsStreamKeyRef()));
    }

    public record StreamInfo(String ingestEndpoint, String streamKey) {
    }

    /** 시작·종료는 상태를 바꾸므로 행을 잠그고 읽는다 — 동시 요청을 직렬화한다. */
    private LiveSession loadOwned(UUID sellerId, UUID liveId) {
        return sessionRepository.findOwnedForUpdate(liveId, sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}

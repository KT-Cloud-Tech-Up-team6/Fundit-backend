package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.application.project.ProjectContextClient;
import com.fundit.live.application.project.ProjectRewardClient;
import com.fundit.live.application.question.QuestionInsightService;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.event.LiveEventTransport.QuestionsSummarizedEvent;
import com.fundit.live.infrastructure.persistence.event.LiveEventOutboxJpaEntity;
import com.fundit.live.infrastructure.persistence.event.LiveEventOutboxJpaRepository;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
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
    private final ProjectContextClient projectContextClient;
    private final ProjectRewardClient projectRewardClient;
    private final QuestionInsightService questionInsightService;

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
        appendOutbox(saved, LiveEventOutboxJpaEntity.TYPE_LIVE_STARTED, now);
        schedulePrepare(saved);
        return saved;
    }

    /**
     * AI 상품정보 색인({@code prepare})은 트랜잭션 커밋 후에 호출한다 — AI가 느리거나 실패해도
     * 방송 시작 자체가 지연되거나 롤백되면 안 된다(요구사항정의서 6.4.4.2와 같은 원칙).
     */
    private void schedulePrepare(LiveSession session) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    ProjectContextClient.ProjectContext context = projectContextClient
                            .find(session.getProjectId()).orElse(null);
                    List<AiClient.RewardInfo> rewards = projectRewardClient.findRewards(session.getProjectId());
                    List<AiClient.KnowledgeChunk> knowledge = context == null
                            ? List.of() : buildKnowledge(context.introTexts());
                    aiClient.prepare(session.getPublicId().toString(), new AiClient.PrepareRequest(
                            context == null ? null : context.title(),
                            context == null ? null : context.categoryMajor(),
                            context == null ? null : context.categoryMinor(),
                            null, session.getProjectId().toString(), knowledge, rewards));
                } catch (RuntimeException e) {
                    log.warn("AI prepare 실패, liveId={}", session.getPublicId(), e);
                }
            }
        });
    }

    private List<AiClient.KnowledgeChunk> buildKnowledge(List<String> introTexts) {
        List<AiClient.KnowledgeChunk> chunks = new ArrayList<>();
        for (int i = 0; i < introTexts.size(); i++) {
            chunks.add(new AiClient.KnowledgeChunk("intro-" + i, "상세설명", introTexts.get(i), false, "project-intro"));
        }
        return chunks;
    }

    @Transactional
    public LiveSession end(UUID sellerId, UUID liveId) {
        LiveSession session = loadOwned(sellerId, liveId);
        Instant now = Instant.now();
        session.end(now);
        LiveSession saved = sessionRepository.save(session);
        // live.ended.v1은 방송 후 자산(질문요약·하이라이트) 두 종류의 유일한 트리거다.
        // 유실되면 방송이 이미 끝나서 재생성할 방법이 없다.
        appendOutbox(saved, LiveEventOutboxJpaEntity.TYPE_LIVE_ENDED, now);
        scheduleQuestionsSummarized(sellerId, saved);
        return saved;
    }

    /**
     * 방송 종료 시점에 AI 최종 집계를 한 번 더 반영({@code QuestionInsightService.faq}가 upsert까지
     * 끝낸다) 한 뒤 그 결과로 {@code live.questions-summarized.v1}을 발행한다 — project-service의
     * LIVE 검증 탭이 이 이벤트로만 채워진다(`LiveDomainApiSpec.md` "질문요약 발행" 절, 담당자 협의
     * 확정). AI 실패로 방송 종료 자체가 막히면 안 되므로 트랜잭션 커밋 후에 호출한다.
     */
    private void scheduleQuestionsSummarized(UUID sellerId, LiveSession session) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    List<LiveQuestionSummaryJpaEntity> summaries = questionInsightService.faq(
                            sellerId, session.getPublicId(), FINAL_SUMMARY_TOP_N);
                    appendQuestionsSummarizedOutbox(session, summaries);
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

    private void appendOutbox(LiveSession session, String eventType, Instant occurredAt) {
        String payload = """
                {"liveId":"%s","projectId":"%s","occurredAt":"%s"}"""
                .formatted(session.getPublicId(), session.getProjectId(), occurredAt);
        outboxRepository.save(LiveEventOutboxJpaEntity.builder()
                .eventType(eventType)
                .liveSessionId(session.getId())
                .payload(payload)
                .build());
    }

    /** 시작·종료는 상태를 바꾸므로 행을 잠그고 읽는다 — 동시 요청을 직렬화한다. */
    private LiveSession loadOwned(UUID sellerId, UUID liveId) {
        return sessionRepository.findOwnedForUpdate(liveId, sellerId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
}

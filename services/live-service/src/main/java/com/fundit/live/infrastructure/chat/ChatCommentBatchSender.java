package com.fundit.live.infrastructure.chat;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaEntity;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaRepository;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaEntity;
import com.fundit.live.infrastructure.persistence.question.LiveQuestionSummaryJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 방송 중 채팅을 3초마다 모아 AI에 배치로 넘긴다(AI팀 실계약 A-3, 최대 50건/배치 권장).
 *
 * <p>Firehose가 메시지 단위로 호출하는 {@code ChatIngestService.ingest()}에 AI 호출을 직접
 * 끼우지 않는 이유와 같다 — 그건 동기 경로라 거기서 AI를 부르면 우리가 느려질 때
 * 시청자 채팅이 같이 느려진다. 배치·주기를 분리해 이 스케줄러만 AI 지연을 흡수한다.
 *
 * <p><b>AI 호출이 실패해도 채팅 저장·송출에는 영향이 없다</b>(CLAUDE.md 원칙) — 실패한 세션은
 * {@code sent_to_ai_at}을 채우지 않고 다음 주기에 그대로 재시도한다.
 */
@Component
@RequiredArgsConstructor
public class ChatCommentBatchSender {

    private static final Logger log = LoggerFactory.getLogger(ChatCommentBatchSender.class);

    private final LiveSessionJpaRepository sessionRepository;
    private final ChatMessageJpaRepository chatMessageRepository;
    private final LiveQuestionSummaryJpaRepository summaryRepository;
    private final AiClient aiClient;

    @Scheduled(fixedDelayString = "${live.ai.comments-poll-interval-ms:3000}")
    public void sendPending() {
        for (LiveSessionJpaEntity session : sessionRepository.findByStatusOrderByActualStartAtDesc(LiveStatus.LIVE)) {
            sendPendingFor(session);
        }
    }

    @Transactional
    void sendPendingFor(LiveSessionJpaEntity session) {
        List<ChatMessageJpaEntity> pending = chatMessageRepository
                .findFirst50BySessionIdAndSentToAiAtIsNullOrderBySentAtAsc(session.getId());
        if (pending.isEmpty()) {
            return;
        }

        List<AiClient.CommentInput> comments = pending.stream()
                .map(m -> new AiClient.CommentInput(String.valueOf(m.getId()), m.getContent(),
                        elapsedMs(session, m.getSentAt()), m.getSenderId()))
                .toList();

        AiClient.CommentBatchResult result;
        try {
            result = aiClient.submitComments(session.getPublicId().toString(), comments);
        } catch (DependencyFailureException e) {
            log.warn("AI 댓글 배치 전송 실패, 다음 주기에 재시도. sessionId={}", session.getId(), e);
            return;
        }

        for (AiClient.AnsweredQuestion q : result.questions()) {
            upsertSummary(session.getId(), q);
        }
        // 성공적으로 응답이 온 건(질문이든 무시든)만 전송 완료 처리한다 — errors[]에 실린 건은
        // 다음 배치에서 그대로 재시도해야 하므로 뺀다.
        List<Long> sentIds = pending.stream()
                .map(ChatMessageJpaEntity::getId)
                .filter(id -> result.errors().stream().noneMatch(e -> e.commentId().equals(String.valueOf(id))))
                .toList();
        if (!sentIds.isEmpty()) {
            chatMessageRepository.markSentToAi(sentIds, Instant.now());
        }
    }

    /** AI의 {@code at_ms}는 방송 시작 기준 경과 ms다. */
    private long elapsedMs(LiveSessionJpaEntity session, Instant sentAt) {
        Instant start = session.getActualStartAt();
        return start == null ? 0 : Math.max(0, sentAt.toEpochMilli() - start.toEpochMilli());
    }

    /**
     * AI가 이 댓글을 FAQ 클러스터로 묶었으면({@code handledBy != UNANSWERABLE}이라도 클러스터
     * ID 없이 개별 응답만 오는 경우도 있어) 로컬에 없는 클러스터일 수 있다 — 그때는 최소 정보로
     * 새로 만든다. 이미 있으면 다음 {@code GET /faq} 폴링 때 {@link LiveQuestionSummaryJpaEntity#applyFromAi}가
     * 대표문구·건수를 채운다. 여기서는 "이 클러스터가 존재한다"만 보장한다.
     */
    private void upsertSummary(Long sessionId, AiClient.AnsweredQuestion q) {
        if (q.handledBy() == AiClient.HandledBy.UNANSWERABLE || q.answer() == null) {
            return;
        }
        // A-3 응답의 questionId(q_0001류)는 클러스터 qid가 아니라 로컬에 저장할 키가 없다 —
        // 이 댓글 자체를 즉시 답변된 개별 건으로 남긴다(세션 안에서 댓글 단위 유일하므로
        // ai_question_id에 이 값을 그대로 써도 다음 GET /faq 폴링이 진짜 qid로 갱신하기 전까지의
        // 임시 식별자로 충돌하지 않는다).
        boolean exists = summaryRepository.findBySessionIdAndAiQuestionId(sessionId, q.questionId()).isPresent();
        if (exists) {
            return;
        }
        summaryRepository.save(LiveQuestionSummaryJpaEntity.builder()
                .publicId(UuidCreator.getTimeOrderedEpoch())
                .sessionId(sessionId)
                .aiQuestionId(q.questionId())
                .topic(q.category())
                .summaryText(q.text())
                .relatedQuestionCount(1)
                .handledBy(q.handledBy())
                .answeredBy(AiClient.AnsweredBy.AI)
                .answered(true)
                .answerText(q.answer().text())
                .answeredAt(Instant.now())
                .build());
    }
}

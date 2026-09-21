package com.fundit.live.infrastructure.chat;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ai.AiClient;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaEntity;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final AiClient aiClient;

    @Scheduled(fixedDelayString = "${live.ai.comments-poll-interval-ms:3000}")
    public void sendPending() {
        // ponytail: 세션을 순서대로 돈다. 동시 LIVE가 늘어 한 주기가 3초를 넘기면 bounded executor로 나눈다.
        for (LiveSessionJpaEntity session : sessionRepository.findByStatusOrderByActualStartAtDesc(LiveStatus.LIVE)) {
            sendPendingFor(session);
        }
    }

    /**
     * 트랜잭션을 걸지 않는다 — AI 호출이 최대 1분이라 그동안 DB 커넥션을 잡을 이유가 없다.
     * 쓰기는 {@code markSentToAi} 한 번뿐이고 그 메서드가 자체 트랜잭션을 연다.
     */
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

        // AI가 처리했다고 응답한 건(질문이든 무시든)만 전송 완료 처리한다. errors[]에 실렸거나
        // 응답에서 빠진 건은 다음 배치에서 다시 보낸다. 요청하지 않은 ID는 pending과의 교집합에서 걸러진다.
        Set<String> handled = new HashSet<>();
        result.questions().forEach(q -> handled.add(q.commentId()));
        result.ignored().forEach(i -> handled.add(i.commentId()));
        List<Long> sentIds = pending.stream()
                .map(ChatMessageJpaEntity::getId)
                .filter(id -> handled.contains(String.valueOf(id)))
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
}

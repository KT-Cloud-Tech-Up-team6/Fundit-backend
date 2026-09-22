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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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

        List<ChatMessageJpaEntity> toSend = withoutRepeats(pending);
        // 걸러낸 도배분도 완료로 찍는다 — 안 찍으면 다음 주기에 다시 골라져 영원히 남는다.
        // 발신자별 첫 건은 항상 남으므로 toSend가 비는 경우는 없다.
        List<Long> skippedIds = pending.stream()
                .filter(m -> !toSend.contains(m))
                .map(ChatMessageJpaEntity::getId)
                .toList();

        List<AiClient.CommentInput> comments = toSend.stream()
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

        // AI가 응답에 실어 보낸 건(질문·무시·실패)은 전부 전송 완료로 찍는다. 요청하지 않은 ID는
        // pending과의 교집합에서 걸러진다.
        //
        // errors[]까지 완료로 찍는 이유: 조회가 sent_at 오름차순이라 실패건을 남겨두면 그게 계속
        // 배치 앞자리를 차지해 뒤 채팅이 방송 끝까지 AI에 도달하지 못한다. 개별 댓글 LLM 실패는
        // 재시도해도 같은 결과일 가능성이 높고, 잦아지면 그건 AI 서버 장애라 재시도 횟수로 풀
        // 문제가 아니다. 버린 건 로그로 남긴다.
        Set<String> handled = new HashSet<>();
        result.questions().forEach(q -> handled.add(q.commentId()));
        result.ignored().forEach(i -> handled.add(i.commentId()));
        result.errors().forEach(e -> {
            handled.add(e.commentId());
            log.warn("AI 댓글 분석 실패, 재전송하지 않고 버린다. sessionId={} commentId={} code={} message={}",
                    session.getId(), e.commentId(), e.code(), e.message());
        });
        List<Long> sentIds = new ArrayList<>(skippedIds);
        toSend.stream()
                .map(ChatMessageJpaEntity::getId)
                .filter(id -> handled.contains(String.valueOf(id)))
                .forEach(sentIds::add);
        if (!sentIds.isEmpty()) {
            chatMessageRepository.markSentToAi(sentIds, Instant.now());
        }
    }

    /**
     * 같은 사람이 직전에 보낸 것과 똑같은 내용을 반복하면 AI로 넘기지 않는다(요청서 7절 "1차 필터는 BE").
     * 채팅 1건이 곧 LLM 호출 1건이라 도배는 그대로 비용이 된다.
     *
     * <p>ponytail: 발신자별 "직전과 동일"만 본다. 문구를 조금씩 바꾸는 도배나 여러 계정을 쓰는
     * 도배는 그대로 통과한다 — 실제로 그런 패턴이 관측되면 그때 유사도·발신자별 한도를 붙인다.
     */
    private List<ChatMessageJpaEntity> withoutRepeats(List<ChatMessageJpaEntity> pending) {
        Map<UUID, String> lastBySender = new HashMap<>();
        List<ChatMessageJpaEntity> kept = new ArrayList<>(pending.size());
        for (ChatMessageJpaEntity m : pending) {
            if (m.getSenderId() != null
                    && Objects.equals(lastBySender.put(m.getSenderId(), m.getContent()), m.getContent())) {
                continue;
            }
            kept.add(m);
        }
        return kept;
    }

    /** AI의 {@code at_ms}는 방송 시작 기준 경과 ms다. */
    private long elapsedMs(LiveSessionJpaEntity session, Instant sentAt) {
        Instant start = session.getActualStartAt();
        return start == null ? 0 : Math.max(0, sentAt.toEpochMilli() - start.toEpochMilli());
    }
}

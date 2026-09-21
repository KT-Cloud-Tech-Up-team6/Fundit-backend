package com.fundit.live.infrastructure.persistence.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageJpaEntity, Long> {

    /**
     * Firehose는 재전송이 가능해서 같은 메시지가 두 번 이상 온다.
     * {@code ivs_message_id} UNIQUE + ON CONFLICT DO NOTHING으로 흡수한다 —
     * "있는지 조회 후 저장"으로 하면 동시 수신에서 둘 다 통과해 중복이 쌓인다.
     *
     * <p>반환값은 실제 적재 여부(0=중복)다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO chat_messages (ivs_message_id, session_id, sender_id, content, sent_at)
            VALUES (:ivsMessageId, :sessionId, :senderId, :content, :sentAt)
            ON CONFLICT (ivs_message_id) DO NOTHING
            """, nativeQuery = true)
    int insertIgnoringConflict(@Param("ivsMessageId") String ivsMessageId,
                               @Param("sessionId") Long sessionId,
                               @Param("senderId") UUID senderId,
                               @Param("content") String content,
                               @Param("sentAt") Instant sentAt);

    /**
     * 다시보기 시간대별 채팅(요구사항정의서 11.4.4). <b>구간</b>으로 받는다 —
     * 시점 1개씩 왕복하면 요청 수가 방송 길이만큼 늘어난다.
     */
    List<ChatMessageJpaEntity> findBySessionIdAndSentAtBetweenOrderBySentAtAsc(
            Long sessionId, Instant from, Instant to);

    /** 대표질문 원본 조회(요구사항정의서 6.4.4.3). 과거 데이터 호환용 — 신규 흐름은 AI에 위임한다. */
    List<ChatMessageJpaEntity> findByQuestionSummaryIdOrderBySentAtAsc(Long questionSummaryId);

    /**
     * AI 배치 전송기 대상. 방송 중 세션 하나당 최대 50건씩 오래된 순으로 뽑는다 —
     * AI 문서가 "배치 최대 50건"을 명시했다.
     */
    List<ChatMessageJpaEntity> findFirst50BySessionIdAndSentToAiAtIsNullOrderBySentAtAsc(Long sessionId);

    /**
     * 전송 완료 표시. {@code IN} 절로 한 번에 처리한다 — 건별 UPDATE는 배치 50건이면
     * 쿼리 50번이 된다.
     */
    @Modifying
    @Query("update ChatMessageJpaEntity c set c.sentToAiAt = :sentAt where c.id in :ids")
    void markSentToAi(@Param("ids") List<Long> ids, @Param("sentAt") java.time.Instant sentAt);
}

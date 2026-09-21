package com.fundit.live.infrastructure.persistence.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 단순 애그리거트 — 적재와 조회가 전부다(persistence-convention.md 2번). */
@Getter
@Entity
@Builder
@Table(name = "chat_messages")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ivs_message_id", nullable = false, updatable = false)
    private String ivsMessageId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private UUID senderId;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    /**
     * AI가 어느 대표질문으로 묶었는지. <b>더 이상 이 경로로 채우지 않는다</b> — AI의
     * {@code GET /faq/{qid}/comments}가 클러스터별 원본 댓글을 직접 돌려주므로 로컬에서
     * FK로 연결해 둘 필요가 없다. 과거 데이터 호환을 위해 컬럼만 남겨둔다.
     */
    @Column(name = "question_summary_id")
    private Long questionSummaryId;

    /** AI 배치 전송기가 이 메시지를 보낸 시각. NULL이면 아직 안 보냈다(전송 대상). */
    @Column(name = "sent_to_ai_at")
    private java.time.Instant sentToAiAt;
}

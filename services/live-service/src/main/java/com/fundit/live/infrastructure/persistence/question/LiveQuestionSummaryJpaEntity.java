package com.fundit.live.infrastructure.persistence.question;

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

/** AI 대표질문(요구사항정의서 6.4.4.3). 분석은 AI가 하고 저장만 이 도메인이 한다. */
@Getter
@Entity
@Builder
@Table(name = "live_question_summaries")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveQuestionSummaryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "topic")
    private String topic;

    @Column(name = "summary_text", nullable = false)
    private String summaryText;

    @Column(name = "related_question_count", nullable = false)
    private int relatedQuestionCount;

    @Column(name = "is_answered", nullable = false)
    private boolean answered;

    @Column(name = "answer_text")
    private String answerText;

    @Column(name = "answered_at")
    private Instant answeredAt;

    /**
     * 판매자가 보낸 최종 답변을 남긴다. 채팅으로만 흘려보내면 소비자 Q&A 버튼이
     * 보여줄 게 없다 — 채팅 스트림은 지나가면 끝이다.
     */
    public void recordAnswer(String answerText, Instant answeredAt) {
        this.answerText = answerText;
        this.answeredAt = answeredAt;
        this.answered = true;
    }
}

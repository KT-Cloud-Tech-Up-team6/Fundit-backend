package com.fundit.live.infrastructure.persistence.question;

import com.fundit.live.application.ai.AiClient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** AI의 FAQ 클러스터 id(qid). 댓글 분류 응답의 question_id와는 다른 값이다. */
    @Column(name = "ai_question_id")
    private String aiQuestionId;

    /** AI의 {@code category} — 관심사 분류(예: 사이즈/색상). */
    @Column(name = "topic")
    private String topic;

    @Column(name = "summary_text", nullable = false)
    private String summaryText;

    @Column(name = "related_question_count", nullable = false)
    private int relatedQuestionCount;

    /** 이 질문을 AI가 왜 그렇게 분류했는지(상품/플랫폼/근거없음). 누가 답했는지와는 다른 축이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "handled_by")
    private AiClient.HandledBy handledBy;

    /** 판매자가 답했는지 AI가 즉시 답했는지. {@code is_answered}가 true일 때만 의미 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "answered_by")
    private AiClient.AnsweredBy answeredBy;

    /** AI의 3분 윈도우 TOP3 승격 여부. */
    @Column(name = "promoted", nullable = false)
    private boolean promoted;

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
        this.answeredBy = AiClient.AnsweredBy.SELLER;
    }

    /**
     * AI {@code GET /faq} 응답으로 이 행을 덮어쓴다. 신규 생성과 갱신을 여기 한 곳으로
     * 모은다 — 호출부(스케줄러·조회 서비스)가 매번 필드를 나열하면 필드가 늘 때마다
     * 어느 한쪽이 빠진다.
     */
    public void applyFromAi(AiClient.FaqItem item) {
        this.topic = item.category();
        this.summaryText = item.representativeText();
        this.relatedQuestionCount = item.count();
        this.promoted = item.promoted();
        // NONE은 이미 남긴 답변(판매자 recordAnswer 등)을 지우지 않는다 — answered=true인데
        // answeredBy=NONE인 모순 행이 생긴다. AI 계약에 "답변 철회"는 없다.
        if (item.answeredBy() == AiClient.AnsweredBy.NONE) {
            if (!this.answered) {
                this.answeredBy = AiClient.AnsweredBy.NONE;
            }
        } else {
            this.answeredBy = item.answeredBy();
            this.answered = true;
            this.answerText = item.answer();
            this.answeredAt = item.answeredAt();
        }
    }
}

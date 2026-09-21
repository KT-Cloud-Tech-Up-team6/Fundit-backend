package com.fundit.live.infrastructure.persistence.question;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiveQuestionSummaryJpaRepository extends JpaRepository<LiveQuestionSummaryJpaEntity, Long> {

    /**
     * 판매자 화면·소비자 Q&A 모달 모두 <b>질문 발생 건수 내림차순</b> 단일 기준이다
     * (요구사항정의서 6.4.4.3 / 11.3.4).
     */
    List<LiveQuestionSummaryJpaEntity> findBySessionIdOrderByRelatedQuestionCountDesc(Long sessionId);

    /** 소비자 Q&A 버튼 — 답변된 질문만 모아본다(요구사항정의서 11.3.4). */
    List<LiveQuestionSummaryJpaEntity> findBySessionIdAndAnsweredTrueOrderByRelatedQuestionCountDesc(
            Long sessionId);

    /**
     * <b>소속 세션을 조회에 묶는다.</b> publicId만으로 찾으면 자기 LIVE의 liveId에 남의 questionId를
     * 붙여 남의 질문을 읽거나 답변을 기록할 수 있다(IDOR). 호출부에서 {@code if}로 대조하면
     * 경로가 늘 때 빠진다 — 실제로 세 경로 중 한 곳도 대조하지 않고 있었다(security.md S4).
     */
    Optional<LiveQuestionSummaryJpaEntity> findByPublicIdAndSessionId(UUID publicId, Long sessionId);

    Optional<LiveQuestionSummaryJpaEntity> findByPublicId(UUID publicId);

    /** AI 클러스터(qid) 단위 upsert 판단용. {@code aiQuestionId}가 null인 과거 행은 대상이 아니다. */
    Optional<LiveQuestionSummaryJpaEntity> findBySessionIdAndAiQuestionId(Long sessionId, String aiQuestionId);
}

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

    Optional<LiveQuestionSummaryJpaEntity> findByPublicId(UUID publicId);
}

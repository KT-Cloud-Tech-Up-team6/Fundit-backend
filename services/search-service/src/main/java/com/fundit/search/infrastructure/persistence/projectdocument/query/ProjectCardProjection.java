package com.fundit.search.infrastructure.persistence.projectdocument.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fundit.search.infrastructure.persistence.projectdocument.ProjectDocumentStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * 홈피드/카테고리탐색/검색 응답 카드 형태로 바로 조회한다 — 도메인 재구성 없이 프로젝션을 그대로
 * presentation 응답으로 반환한다(persistence-convention.md §3).
 */
public interface ProjectCardProjection {

    Long getProjectId();

    String getTitle();

    String getThumbnailUrl();

    String getCategoryMajor();

    String getCategoryMinor();

    ProjectDocumentStatus getStatus();

    Integer getAchievementRate();

    String getSellerDisplayName();

    /** remainingDays 계산 재료일 뿐 응답 필드는 아니다(API 계약에 없음). */
    @JsonIgnore
    Instant getFundingDeadline();

    /** project-service와 동일한 표시코드 생성 규칙(SearchERD.md 설계 결정 6번) — 컬럼 저장 없이 매번 계산한다. */
    default String getProjectDisplayCode() {
        return "F" + String.format("%07d", getProjectId());
    }

    default long getRemainingDays() {
        return Math.max(0, Duration.between(Instant.now(), getFundingDeadline()).toDays());
    }
}

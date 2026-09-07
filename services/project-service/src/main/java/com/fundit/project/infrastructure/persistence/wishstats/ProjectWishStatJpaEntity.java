package com.fundit.project.infrastructure.persistence.wishstats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 단순 애그리거트 — member-service가 발행하는 ProjectWished/ProjectUnwished 이벤트를
 * {@code ProjectWishStatsEventSubscriber}가 구독해 채우는 읽기 모델(PROJECT-016).
 * 행이 없으면 조회 시 0으로 본다.
 */
@Getter
@Entity
@Builder
@Table(name = "project_wish_stats")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectWishStatJpaEntity {

    @Id
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "wish_count", nullable = false)
    private Integer wishCount;
}

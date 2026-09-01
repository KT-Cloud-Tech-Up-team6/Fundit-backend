package com.fundit.project.infrastructure.persistence.category;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PRD 4.2.4 카테고리 마스터. 값 목록은 시드 마이그레이션으로 적재하고 이 서비스는 조회만 한다.
 */
@Getter
@Entity
@Builder
@Table(name = "categories")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryJpaEntity {

    @EmbeddedId
    private CategoryId id;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}

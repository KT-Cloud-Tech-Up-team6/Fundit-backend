package com.fundit.search.infrastructure.persistence.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * project-service {@code categories}의 읽기 전용 미러(SearchERD.md 1번). CRUD API 없이 Flyway 시드로만 관리하고,
 * 이벤트로 동기화되지 않는다 — 단순 애그리거트(persistence-convention.md §2).
 */
@Getter
@Entity
@Builder
@Table(name = "categories")
@IdClass(CategoryId.class)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryJpaEntity {

    @Id
    @Column(name = "category_major", nullable = false, length = 50)
    private String categoryMajor;

    @Id
    @Column(name = "category_minor", nullable = false, length = 50)
    private String categoryMinor;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}

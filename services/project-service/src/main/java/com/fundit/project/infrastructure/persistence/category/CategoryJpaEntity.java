package com.fundit.project.infrastructure.persistence.category;

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
 * 단순 애그리거트(persistence-convention.md §2) — 읽기 전용 마스터 데이터, 시드로만 관리하고
 * 이 서비스에서 생성/수정 API를 두지 않는다(project-service CLAUDE.md).
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
    @Column(name = "category_major")
    private String categoryMajor;

    @Id
    @Column(name = "category_minor")
    private String categoryMinor;

    @Column(name = "display_order")
    private int displayOrder;
}

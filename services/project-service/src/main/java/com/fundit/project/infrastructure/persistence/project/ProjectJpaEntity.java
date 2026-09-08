package com.fundit.project.infrastructure.persistence.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * updated_at은 DB 트리거(trg_projects_updated_at)가 UPDATE 시점에 항상 CURRENT_TIMESTAMP로
 * 덮어쓰므로, 다른 서비스와 달리 여기서는 @PreUpdate로 애플리케이션 시각을 세팅하지 않는다
 * (세팅해도 트리거가 다시 덮어써 의미가 없음 — V1__init_schema.sql 참고).
 * project_display_code는 DB GENERATED ALWAYS AS ... STORED 컬럼이라 insertable/updatable을 막는다.
 */
@Getter
@Entity
@Builder
@Table(name = "projects")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false)
    private UUID publicId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    // business_type~goal_amount: 생성 직후(DRAFT, 값 없음) 상태가 있어 전부 nullable
    // (V2__add_project_story_and_privacy_consent.sql에서 DB NOT NULL을 제거함).
    @Column(name = "business_type", length = 10)
    private String businessType;

    @Column(name = "category_major", length = 50)
    private String categoryMajor;

    @Column(name = "category_minor", length = 50)
    private String categoryMinor;

    @Column(length = 40)
    private String title;

    @Column(name = "goal_amount")
    private Long goalAmount;

    @Column(name = "funding_start_at")
    private Instant fundingStartAt;

    @Column(name = "funding_deadline")
    private Instant fundingDeadline;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "cover_image_url", columnDefinition = "TEXT")
    private String coverImageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "intro_content", columnDefinition = "jsonb")
    private List<IntroContentBlockEntity> introContent;

    @Generated(event = EventType.INSERT)
    @Column(name = "project_display_code", insertable = false, updatable = false)
    private String projectDisplayCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // trg_projects_updated_at이 UPDATE 시 값을 덮어쓰므로, INSERT/UPDATE 직후 트리거가
    // 실제로 채운 값을 다시 읽어오도록 @Generated(UPDATE)를 붙인다.
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
    }
}

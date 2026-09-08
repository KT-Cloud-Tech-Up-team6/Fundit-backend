package com.fundit.project.infrastructure.persistence.reward;

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
import org.hibernate.generator.EventType;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/**
 * updated_at은 DB 트리거(trg_rewards_updated_at)가 관리하므로 @PreUpdate를 두지 않는다
 * (ProjectJpaEntity와 동일한 이유 — V1__init_schema.sql 참고).
 * reward_display_code는 DB GENERATED ALWAYS AS ... STORED 컬럼.
 */
@Getter
@Entity
@Builder
@Table(name = "rewards")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RewardJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(nullable = false)
    private Long price;

    @Column(name = "is_limited", nullable = false)
    private Boolean isLimited;

    private Integer quantity;

    @Column(name = "is_early_bird", nullable = false)
    private Boolean isEarlyBird;

    @Column(name = "has_option", nullable = false)
    private Boolean hasOption;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "category_type", length = 30)
    private String categoryType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> disclosure;

    @Column(name = "simple_refund_disabled", nullable = false)
    private Boolean simpleRefundDisabled;

    @Generated(event = EventType.INSERT)
    @Column(name = "reward_display_code", insertable = false, updatable = false)
    private String rewardDisplayCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // trg_rewards_updated_at이 UPDATE 시 값을 덮어쓰므로 ProjectJpaEntity와 동일하게 처리한다.
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
        if (this.sortOrder == null) this.sortOrder = 0;
        if (this.hasOption == null) this.hasOption = false;
        if (this.simpleRefundDisabled == null) this.simpleRefundDisabled = false;
    }
}

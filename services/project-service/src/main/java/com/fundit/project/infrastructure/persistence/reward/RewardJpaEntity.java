package com.fundit.project.infrastructure.persistence.reward;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

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

    @Column(name = "project_id", nullable = false, updatable = false)
    private Long projectId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "is_unlimited", nullable = false)
    private Boolean unlimited;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "category_type", length = 30)
    private String categoryType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "disclosure")
    private Map<String, Object> disclosure;

    @Column(name = "is_early_bird", nullable = false)
    private Boolean earlyBird;

    @Column(name = "simple_refund_disabled", nullable = false)
    private Boolean simpleRefundDisabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
        if (this.displayOrder == null) this.displayOrder = 0;
        if (this.unlimited == null) this.unlimited = false;
        if (this.earlyBird == null) this.earlyBird = false;
        if (this.simpleRefundDisabled == null) this.simpleRefundDisabled = false;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

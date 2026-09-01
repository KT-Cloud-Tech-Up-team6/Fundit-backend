package com.fundit.project.infrastructure.persistence.project;

import com.fundit.project.domain.project.BusinessType;
import com.fundit.project.domain.project.ProjectStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.UUID;

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

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private UUID sellerId;

    @Column(name = "category_major", length = 50)
    private String categoryMajor;

    @Column(name = "category_minor", length = 50)
    private String categoryMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", length = 20)
    private BusinessType businessType;

    @Column(name = "privacy_agreed", nullable = false)
    private Boolean privacyAgreed;

    @Column(name = "title", length = 40)
    private String title;

    @Column(name = "thumbnail_image_url")
    private String thumbnailImageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "intro_content")
    private Map<String, Object> introContent;

    @Column(name = "goal_amount")
    private Long goalAmount;

    @Column(name = "funding_start_at")
    private Instant fundingStartAt;

    @Column(name = "funding_deadline")
    private Instant fundingDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectStatus status;

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
        if (this.privacyAgreed == null) this.privacyAgreed = false;
        if (this.status == null) this.status = ProjectStatus.DRAFT;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

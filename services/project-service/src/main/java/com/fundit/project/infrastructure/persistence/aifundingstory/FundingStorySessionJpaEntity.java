package com.fundit.project.infrastructure.persistence.aifundingstory;

import com.fundit.project.domain.aifundingstory.FundingStoryAdditionalQuestion;
import com.fundit.project.domain.aifundingstory.FundingStoryAnswer;
import com.fundit.project.domain.aifundingstory.FundingStoryResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.List;
import java.util.UUID;

/** DB 컬럼 매핑 전용. 상태 전이는 {@code FundingStorySession} 도메인이 담당한다. */
@Getter
@Entity
@Builder
@Table(name = "ai_funding_story_sessions")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FundingStorySessionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "product_description", nullable = false, columnDefinition = "TEXT")
    private String productDescription;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "product_image_urls", columnDefinition = "jsonb")
    private List<String> productImageUrls;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<FundingStoryAnswer> answers;

    @Column(nullable = false, length = 20)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_questions", columnDefinition = "jsonb")
    private List<FundingStoryAdditionalQuestion> additionalQuestions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private FundingStoryResult result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.updatedAt == null) this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

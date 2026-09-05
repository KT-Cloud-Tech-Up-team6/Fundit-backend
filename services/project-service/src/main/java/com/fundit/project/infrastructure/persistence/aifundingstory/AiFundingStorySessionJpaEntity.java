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

/**
 * 단순 애그리거트(persistence-convention.md §2) — 상태(GENERATING/COMPLETED/FAILED)는 있지만
 * 이 슬라이스에서는 목(mock) 생성기가 동기적으로 즉시 완료 처리하므로 복잡한 전이 규칙이 없다.
 * 실제 외부 AI 비동기 연동이 붙으면 그때 복잡 애그리거트로 전환을 검토한다.
 */
@Getter
@Entity
@Builder
@Table(name = "ai_funding_story_sessions")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiFundingStorySessionJpaEntity {

    public static final String STATUS_GENERATING = "GENERATING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

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
        if (this.status == null) this.status = STATUS_GENERATING;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void completeWith(FundingStoryResult result, List<FundingStoryAdditionalQuestion> additionalQuestions) {
        this.result = result;
        this.additionalQuestions = additionalQuestions;
        this.status = STATUS_COMPLETED;
    }

    public void fail() {
        this.status = STATUS_FAILED;
    }
}

package com.fundit.project.fixture;

import com.fundit.project.domain.project.BusinessType;
import com.fundit.project.domain.project.Project;
import com.fundit.project.domain.project.ProjectStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class ProjectFixture {

    public static final UUID SELLER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    public static final UUID OTHER_MEMBER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    public static final UUID PUBLIC_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    public static final Long PROJECT_ID = 1L;

    public static final String CATEGORY_MAJOR = "홈·리빙";
    public static final String CATEGORY_MINOR = "인테리어";

    private ProjectFixture() {
    }

    /** 상세페이지까지 다 채워 검수 요청만 남은 DRAFT. */
    public static Project draft() {
        return base().build();
    }

    /** 생성 직후 상태 — 제목·소개·대표이미지가 전부 비어 있다. */
    public static Project emptyDraft() {
        return base()
                .title(null)
                .thumbnailImageUrl(null)
                .introContent(null)
                .goalAmount(null)
                .categoryMajor(null)
                .categoryMinor(null)
                .privacyAgreed(false)
                .build();
    }

    public static Project pendingReview() {
        return base().status(ProjectStatus.PENDING_REVIEW).build();
    }

    public static Project ongoing() {
        return base()
                .status(ProjectStatus.ONGOING)
                .fundingStartAt(Instant.parse("2026-09-01T00:00:00Z"))
                .fundingDeadline(Instant.parse("2026-10-01T00:00:00Z"))
                .build();
    }

    public static Project.ProjectBuilder base() {
        return Project.builder()
                .id(PROJECT_ID)
                .publicId(PUBLIC_ID)
                .sellerId(SELLER_ID)
                .categoryMajor(CATEGORY_MAJOR)
                .categoryMinor(CATEGORY_MINOR)
                .businessType(BusinessType.GENERAL)
                .privacyAgreed(true)
                .title("무선 미니 가습기")
                .thumbnailImageUrl("https://cdn.example.com/p/1.jpg")
                .introContent(Map.of("text", "소개 본문"))
                .goalAmount(5_000_000L)
                .status(ProjectStatus.DRAFT)
                .createdAt(Instant.parse("2026-08-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-08-01T00:00:00Z"));
    }
}

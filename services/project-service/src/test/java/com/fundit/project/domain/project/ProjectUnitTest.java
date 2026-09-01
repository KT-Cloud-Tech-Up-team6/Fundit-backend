package com.fundit.project.domain.project;

import com.fundit.project.fixture.ProjectFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Project 도메인")
class ProjectUnitTest {

    @Nested
    class 생성 {

        @Test
        void 입력값_없이_DRAFT로_만들어진다() {
            // given & when
            Project project = Project.createDraft(ProjectFixture.SELLER_ID);

            // then
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);
            assertThat(project.getSellerId()).isEqualTo(ProjectFixture.SELLER_ID);
            assertThat(project.getTitle()).isNull();
            assertThat(project.getGoalAmount()).isNull();
        }

        @Test
        void 개인정보_동의는_동의하지_않은_상태로_시작한다() {
            // given & when
            Project project = Project.createDraft(ProjectFixture.SELLER_ID);

            // then
            assertThat(project.isPrivacyAgreed()).isFalse();
        }

        @Test
        void 외부_식별자가_자동으로_부여된다() {
            // given & when
            Project first = Project.createDraft(ProjectFixture.SELLER_ID);
            Project second = Project.createDraft(ProjectFixture.SELLER_ID);

            // then
            assertThat(first.getPublicId()).isNotNull();
            assertThat(first.getPublicId()).isNotEqualTo(second.getPublicId());
        }
    }

    @Nested
    class 소유자_판정 {

        @Test
        void 판매자_본인이면_참이다() {
            // given
            Project project = ProjectFixture.draft();

            // when & then
            assertThat(project.isOwnedBy(ProjectFixture.SELLER_ID)).isTrue();
        }

        @Test
        void 다른_회원이면_거짓이다() {
            // given
            Project project = ProjectFixture.draft();

            // when & then
            assertThat(project.isOwnedBy(ProjectFixture.OTHER_MEMBER_ID)).isFalse();
        }

        @Test
        void 비로그인이면_거짓이다() {
            // given
            Project project = ProjectFixture.draft();

            // when & then
            assertThat(project.isOwnedBy(null)).isFalse();
        }
    }

    @Nested
    class 노출_판정 {

        @Test
        void 진행중이면_비로그인에게도_보인다() {
            // given
            Project project = ProjectFixture.ongoing();

            // when & then
            assertThat(project.isVisibleTo(null)).isTrue();
        }

        @Test
        void 작성중이면_소유자에게만_보인다() {
            // given
            Project project = ProjectFixture.draft();

            // when & then
            assertThat(project.isVisibleTo(ProjectFixture.SELLER_ID)).isTrue();
            assertThat(project.isVisibleTo(ProjectFixture.OTHER_MEMBER_ID)).isFalse();
        }

        @Test
        void 검수중이면_소유자에게만_보인다() {
            // given
            Project project = ProjectFixture.pendingReview();

            // when & then
            assertThat(project.isVisibleTo(ProjectFixture.SELLER_ID)).isTrue();
            assertThat(project.isVisibleTo(ProjectFixture.OTHER_MEMBER_ID)).isFalse();
        }
    }

    @Nested
    class 기본정보_수정 {

        @Test
        void 정상_입력이면_반영된다() {
            // given
            Project project = ProjectFixture.emptyDraft();

            // when
            project.updateBasicInfo(BusinessType.CORPORATION, "테크·가전", "음향기기", 1_000_000L, true);

            // then
            assertThat(project.getBusinessType()).isEqualTo(BusinessType.CORPORATION);
            assertThat(project.getCategoryMajor()).isEqualTo("테크·가전");
            assertThat(project.getCategoryMinor()).isEqualTo("음향기기");
            assertThat(project.getGoalAmount()).isEqualTo(1_000_000L);
            assertThat(project.isPrivacyAgreed()).isTrue();
        }

        @Test
        void 목표금액이_최소액과_같아도_통과한다() {
            // given
            Project project = ProjectFixture.emptyDraft();

            // when
            project.updateBasicInfo(BusinessType.GENERAL, "홈·리빙", "인테리어", 500_000L, true);

            // then
            assertThat(project.getGoalAmount()).isEqualTo(500_000L);
        }

        @Test
        void 수정_시각이_갱신된다() {
            // given
            Project project = ProjectFixture.emptyDraft();
            Instant before = project.getUpdatedAt();

            // when
            project.updateBasicInfo(BusinessType.GENERAL, "홈·리빙", "인테리어", 500_000L, true);

            // then
            assertThat(project.getUpdatedAt()).isAfter(before);
        }
    }

    @Nested
    class 상세페이지_수정 {

        @Test
        void 전달한_값만_반영되고_생략한_값은_유지된다() {
            // given
            Project project = ProjectFixture.draft();
            String originalThumbnail = project.getThumbnailImageUrl();

            // when
            project.updateDetail("새 제목", null, null);

            // then
            assertThat(project.getTitle()).isEqualTo("새 제목");
            assertThat(project.getThumbnailImageUrl()).isEqualTo(originalThumbnail);
            assertThat(project.getIntroContent()).isNotNull();
        }

        @Test
        void 제목이_최대_길이면_통과한다() {
            // given
            Project project = ProjectFixture.draft();
            String maxLengthTitle = "가".repeat(40);

            // when
            project.updateDetail(maxLengthTitle, null, null);

            // then
            assertThat(project.getTitle()).isEqualTo(maxLengthTitle);
        }

        @Test
        void 소개_내용이_교체된다() {
            // given
            Project project = ProjectFixture.draft();

            // when
            project.updateDetail(null, null, Map.of("text", "바뀐 본문"));

            // then
            assertThat(project.getIntroContent()).containsEntry("text", "바뀐 본문");
        }
    }

    @Nested
    class 검수_요청 {

        @Test
        void 필수값이_모두_채워져_있으면_검수_대기로_넘어간다() {
            // given
            Project project = ProjectFixture.draft();

            // when
            project.submitForReview(true);

            // then
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.PENDING_REVIEW);
        }
    }

    @Nested
    class 검수_처리 {

        @Test
        void 승인하면_진행중으로_바뀌고_일정이_확정된다() {
            // given
            Project project = ProjectFixture.pendingReview();
            Instant startAt = Instant.parse("2026-09-10T00:00:00Z");
            Instant deadline = Instant.parse("2026-10-10T00:00:00Z");

            // when
            project.approveReview(startAt, deadline);

            // then
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.ONGOING);
            assertThat(project.getFundingStartAt()).isEqualTo(startAt);
            assertThat(project.getFundingDeadline()).isEqualTo(deadline);
        }

        @Test
        void 반려하면_작성중으로_되돌아간다() {
            // given
            Project project = ProjectFixture.pendingReview();

            // when
            project.rejectReview();

            // then
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        }

        @Test
        void 반려된_뒤에는_다시_수정할_수_있다() {
            // given
            Project project = ProjectFixture.pendingReview();
            project.rejectReview();

            // when
            project.updateDetail("반려 후 수정", null, null);

            // then
            assertThat(project.getTitle()).isEqualTo("반려 후 수정");
        }
    }

    @Nested
    class 삭제 {

        @Test
        void 작성중이면_삭제_시각이_기록된다() {
            // given
            Project project = ProjectFixture.draft();
            Instant now = Instant.parse("2026-08-15T00:00:00Z");

            // when
            project.softDelete(now);

            // then
            assertThat(project.getDeletedAt()).isEqualTo(now);
        }

        @Test
        void 종료된_프로젝트는_삭제할_수_있다() {
            // given
            Project project = ProjectFixture.base().status(ProjectStatus.SUCCEEDED).build();
            Instant now = Instant.parse("2026-08-15T00:00:00Z");

            // when
            project.softDelete(now);

            // then
            assertThat(project.getDeletedAt()).isEqualTo(now);
        }
    }

    @Nested
    class 남은_기간_계산 {

        @Test
        void 마감일까지_남은_일수를_돌려준다() {
            // given
            Project project = ProjectFixture.base()
                    .fundingDeadline(Instant.parse("2026-10-10T23:59:59Z"))
                    .build();

            // when
            Integer dDay = project.dDay(Instant.parse("2026-10-01T00:00:00Z"));

            // then
            assertThat(dDay).isEqualTo(9);
        }

        @Test
        void 마감일이_확정되지_않았으면_null이다() {
            // given
            Project project = ProjectFixture.draft();

            // when
            Integer dDay = project.dDay(Instant.parse("2026-10-01T00:00:00Z"));

            // then
            assertThat(dDay).isNull();
        }
    }

    @Nested
    class 달성률_계산 {

        @Test
        void 목표금액_대비_비율을_정수로_돌려준다() {
            // given
            Project project = ProjectFixture.base().goalAmount(5_000_000L).build();

            // when & then
            assertThat(project.achievementRate(3_200_000L)).isEqualTo(64);
        }

        @Test
        void 목표금액이_없으면_0이다() {
            // given
            Project project = ProjectFixture.base().goalAmount(null).build();

            // when & then
            assertThat(project.achievementRate(3_200_000L)).isZero();
        }

        @Test
        void 목표금액이_0이면_0으로_나누지_않고_0을_돌려준다() {
            // given
            Project project = ProjectFixture.base().goalAmount(0L).build();

            // when & then
            assertThat(project.achievementRate(3_200_000L)).isZero();
        }
    }
}

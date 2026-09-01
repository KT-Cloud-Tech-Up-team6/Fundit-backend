package com.fundit.project.domain.project;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.ProjectErrorCode;
import com.fundit.project.fixture.ProjectFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static com.fundit.project.support.BusinessExceptionAssertions.assertBusinessException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Project 도메인 예외")
class ProjectUnitExceptionTest {

    @Nested
    class 검수_중_수정_잠금 {

        @Test
        void 기본정보를_수정하려_하면_잠김_예외가_발생한다() {
            // given
            Project project = ProjectFixture.pendingReview();

            // when & then
            assertBusinessException(
                    () -> project.updateBasicInfo(BusinessType.GENERAL, "홈·리빙", "인테리어", 500_000L, true),
                    CommonErrorCode.RESOURCE_LOCKED);
        }

        @Test
        void 상세페이지를_수정하려_하면_잠김_예외가_발생한다() {
            // given
            Project project = ProjectFixture.pendingReview();

            // when & then
            assertBusinessException(
                    () -> project.updateDetail("바꾼 제목", null, null),
                    CommonErrorCode.RESOURCE_LOCKED);
        }
    }

    @Nested
    class 기본정보_수정 {

        @Test
        void 개인정보_수집에_동의하지_않으면_예외가_발생한다() {
            // given
            Project project = ProjectFixture.emptyDraft();

            // when & then
            assertBusinessException(
                    () -> project.updateBasicInfo(BusinessType.GENERAL, "홈·리빙", "인테리어", 500_000L, false),
                    ProjectErrorCode.PRIVACY_NOT_AGREED);
        }

        @Test
        void 목표금액이_최소액_미만이면_예외가_발생한다() {
            // given
            Project project = ProjectFixture.emptyDraft();

            // when & then
            assertBusinessException(
                    () -> project.updateBasicInfo(BusinessType.GENERAL, "홈·리빙", "인테리어", 499_999L, true),
                    CommonErrorCode.INVALID_INPUT);
        }

        @Test
        void 목표금액이_비어_있으면_예외가_발생한다() {
            // given
            Project project = ProjectFixture.emptyDraft();

            // when & then
            assertBusinessException(
                    () -> project.updateBasicInfo(BusinessType.GENERAL, "홈·리빙", "인테리어", null, true),
                    CommonErrorCode.INVALID_INPUT);
        }
    }

    @Nested
    class 상세페이지_수정 {

        @Test
        void 제목이_40자를_넘으면_예외가_발생한다() {
            // given
            Project project = ProjectFixture.draft();
            String tooLongTitle = "가".repeat(41);

            // when & then
            assertBusinessException(
                    () -> project.updateDetail(tooLongTitle, null, null),
                    CommonErrorCode.INVALID_INPUT);
        }

        @Test
        void 제목_길이_초과로_실패하면_기존_제목이_보존된다() {
            // given
            Project project = ProjectFixture.draft();
            String originalTitle = project.getTitle();

            // when
            assertThatThrownBy(() -> project.updateDetail("가".repeat(41), null, null))
                    .isInstanceOf(BusinessException.class);

            // then
            assertThat(project.getTitle()).isEqualTo(originalTitle);
        }
    }

    @Nested
    class 검수_요청 {

        @Test
        void 이미_검수_중이면_중복_요청으로_처리된다() {
            // given
            Project project = ProjectFixture.pendingReview();

            // when & then
            assertBusinessException(() -> project.submitForReview(true), CommonErrorCode.CONFLICT);
        }

        @Test
        void 이미_진행중이면_예외가_발생한다() {
            // given
            Project project = ProjectFixture.ongoing();

            // when & then
            assertBusinessException(() -> project.submitForReview(true), CommonErrorCode.CONFLICT);
        }

        @Test
        void 리워드가_없으면_예외가_발생한다() {
            // given
            Project project = ProjectFixture.draft();

            // when & then
            assertBusinessException(() -> project.submitForReview(false), CommonErrorCode.INVALID_INPUT);
        }

        @Test
        void 누락된_필수_항목이_메시지에_모두_담긴다() {
            // given
            Project project = ProjectFixture.emptyDraft();

            // when & then
            assertThatThrownBy(() -> project.submitForReview(false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("title")
                    .hasMessageContaining("thumbnailImageUrl")
                    .hasMessageContaining("introContent")
                    .hasMessageContaining("rewards");
        }

        @Test
        void 소개_내용이_비어_있으면_누락으로_본다() {
            // given
            Project project = ProjectFixture.base().introContent(Map.of()).build();

            // when & then
            assertThatThrownBy(() -> project.submitForReview(true))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("introContent");
        }

        @Test
        void 실패하면_상태가_그대로_유지된다() {
            // given
            Project project = ProjectFixture.draft();

            // when
            assertThatThrownBy(() -> project.submitForReview(false))
                    .isInstanceOf(BusinessException.class);

            // then
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        }
    }

    @Nested
    class 검수_처리 {

        @Test
        void 검수_대기_상태가_아니면_승인할_수_없다() {
            // given
            Project project = ProjectFixture.draft();

            // when & then
            assertBusinessException(
                    () -> project.approveReview(Instant.parse("2026-09-10T00:00:00Z"),
                            Instant.parse("2026-10-10T00:00:00Z")),
                    ProjectErrorCode.PROJECT_REVIEW_NOT_PENDING);
        }

        @Test
        void 검수_대기_상태가_아니면_반려할_수_없다() {
            // given
            Project project = ProjectFixture.ongoing();

            // when & then
            assertBusinessException(project::rejectReview, ProjectErrorCode.PROJECT_REVIEW_NOT_PENDING);
        }

        @Test
        void 승인하면서_펀딩_일정을_주지_않으면_예외가_발생한다() {
            // given
            Project project = ProjectFixture.pendingReview();

            // when & then
            assertBusinessException(
                    () -> project.approveReview(null, Instant.parse("2026-10-10T00:00:00Z")),
                    CommonErrorCode.INVALID_INPUT);
        }

        @Test
        void 마감일시가_시작일시보다_앞서면_예외가_발생한다() {
            // given
            Project project = ProjectFixture.pendingReview();

            // when & then
            assertBusinessException(
                    () -> project.approveReview(Instant.parse("2026-10-10T00:00:00Z"),
                            Instant.parse("2026-09-10T00:00:00Z")),
                    CommonErrorCode.INVALID_INPUT);
        }

        @Test
        void 마감일시가_시작일시와_같으면_예외가_발생한다() {
            // given
            Project project = ProjectFixture.pendingReview();
            Instant sameMoment = Instant.parse("2026-09-10T00:00:00Z");

            // when & then
            assertBusinessException(
                    () -> project.approveReview(sameMoment, sameMoment),
                    CommonErrorCode.INVALID_INPUT);
        }
    }

    @Nested
    class 삭제 {

        @Test
        void 검수_중이면_삭제할_수_없다() {
            // given
            Project project = ProjectFixture.pendingReview();

            // when & then
            assertBusinessException(
                    () -> project.softDelete(Instant.parse("2026-08-15T00:00:00Z")),
                    ProjectErrorCode.PROJECT_NOT_DELETABLE);
        }

        @Test
        void 진행_중이면_삭제할_수_없다() {
            // given
            Project project = ProjectFixture.ongoing();

            // when & then
            assertBusinessException(
                    () -> project.softDelete(Instant.parse("2026-08-15T00:00:00Z")),
                    ProjectErrorCode.PROJECT_NOT_DELETABLE);
        }
    }
}

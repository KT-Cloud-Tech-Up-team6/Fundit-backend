package com.fundit.project.application.project;

import com.fundit.common.error.CommonErrorCode;
import com.fundit.project.domain.project.ProjectStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.fundit.project.support.BusinessExceptionAssertions.assertBusinessException;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProjectListFilter")
class ProjectListFilterUnitTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 값이_없으면_전체_조회로_해석된다(String status) {
        // given & when
        var resolved = ProjectListFilter.resolve(status);

        // then
        assertThat(resolved).isEmpty();
    }

    @Test
    void 개별_상태값은_그대로_해석된다() {
        // given & when
        var resolved = ProjectListFilter.resolve("ONGOING");

        // then
        assertThat(resolved).containsExactly(ProjectStatus.ONGOING);
    }

    @Test
    void 소문자로_들어와도_해석된다() {
        // given & when
        var resolved = ProjectListFilter.resolve("ongoing");

        // then
        assertThat(resolved).containsExactly(ProjectStatus.ONGOING);
    }

    @Test
    void 준비중_탭은_작성중과_검수중을_묶는다() {
        // given & when
        var resolved = ProjectListFilter.resolve("PREPARING");

        // then
        assertThat(resolved).containsExactly(ProjectStatus.DRAFT, ProjectStatus.PENDING_REVIEW);
    }

    @Test
    void 종료_탭은_성공과_실패를_묶는다() {
        // given & when
        var resolved = ProjectListFilter.resolve("CLOSED");

        // then
        assertThat(resolved).containsExactly(ProjectStatus.SUCCEEDED, ProjectStatus.FAILED);
    }

    @Test
    void 지원하지_않는_값이면_예외가_발생한다() {
        // given & when & then
        assertBusinessException(() -> ProjectListFilter.resolve("UNKNOWN"), CommonErrorCode.INVALID_INPUT);
    }
}

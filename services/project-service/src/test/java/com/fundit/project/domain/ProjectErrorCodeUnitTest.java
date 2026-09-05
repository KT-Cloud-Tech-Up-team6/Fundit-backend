package com.fundit.project.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectErrorCodeUnitTest {

    @Test
    void 모든_코드는_이름과_getCode가_같다() {
        for (ProjectErrorCode code : ProjectErrorCode.values()) {
            assertThat(code.getCode()).isEqualTo(code.name());
        }
    }

    @Test
    void 모든_코드는_httpStatus와_message를_갖는다() {
        for (ProjectErrorCode code : ProjectErrorCode.values()) {
            assertThat(code.getHttpStatus()).isPositive();
            assertThat(code.getMessage()).isNotBlank();
        }
    }
}

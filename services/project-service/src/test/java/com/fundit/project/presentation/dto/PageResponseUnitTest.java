package com.fundit.project.presentation.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseUnitTest {

    @Test
    void Page를_PageResponse로_변환한다() {
        // given
        var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 20), 2);

        // when
        PageResponse<String> result = PageResponse.from(page);

        // then
        assertThat(result.content()).containsExactly("a", "b");
        assertThat(result.page()).isZero();
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
    }
}

package com.fundit.search.application.category;

import com.fundit.search.infrastructure.persistence.category.CategoryJpaEntity;
import com.fundit.search.infrastructure.persistence.category.CategoryJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryQueryServiceUnitTest {

    @Mock
    private CategoryJpaRepository categoryJpaRepository;

    @InjectMocks
    private CategoryQueryService categoryQueryService;

    @Test
    void 같은_대분류의_중분류들은_하나의_그룹으로_묶인다() {
        // given
        when(categoryJpaRepository.findAllByOrderByCategoryMajorAscDisplayOrderAsc()).thenReturn(List.of(
                CategoryJpaEntity.builder().categoryMajor("테크·가전").categoryMinor("생활가전").displayOrder(1).build(),
                CategoryJpaEntity.builder().categoryMajor("테크·가전").categoryMinor("모바일·태블릿").displayOrder(2).build(),
                CategoryJpaEntity.builder().categoryMajor("푸드").categoryMinor("가공식품").displayOrder(1).build()
        ));

        // when
        List<CategoryQueryService.CategoryGroup> result = categoryQueryService.getCategoryTree();

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).categoryMajor()).isEqualTo("테크·가전");
        assertThat(result.get(0).categoryMinors()).hasSize(2);
        assertThat(result.get(1).categoryMajor()).isEqualTo("푸드");
        assertThat(result.get(1).categoryMinors()).hasSize(1);
    }

    @Test
    void 중분류는_display_order_오름차순을_유지한다() {
        // given
        when(categoryJpaRepository.findAllByOrderByCategoryMajorAscDisplayOrderAsc()).thenReturn(List.of(
                CategoryJpaEntity.builder().categoryMajor("테크·가전").categoryMinor("생활가전").displayOrder(1).build(),
                CategoryJpaEntity.builder().categoryMajor("테크·가전").categoryMinor("모바일·태블릿").displayOrder(2).build()
        ));

        // when
        List<CategoryQueryService.CategoryGroup> result = categoryQueryService.getCategoryTree();

        // then
        var minors = result.get(0).categoryMinors();
        assertThat(minors.get(0).categoryMinor()).isEqualTo("생활가전");
        assertThat(minors.get(1).categoryMinor()).isEqualTo("모바일·태블릿");
    }
}

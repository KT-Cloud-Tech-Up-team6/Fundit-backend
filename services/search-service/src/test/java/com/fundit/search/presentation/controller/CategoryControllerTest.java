package com.fundit.search.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.search.application.category.CategoryQueryService;
import com.fundit.search.domain.SearchErrorCode;
import com.fundit.common.error.BusinessException;
import com.fundit.search.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** internal-api.key 고정 이유는 NotificationControllerTest와 동일. */
@WebMvcTest(CategoryController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryQueryService categoryQueryService;

    @Test
    void 카테고리_트리를_조회한다() throws Exception {
        // given
        when(categoryQueryService.getCategoryTree()).thenReturn(List.of(
                new CategoryQueryService.CategoryGroup("테크·가전",
                        List.of(new CategoryQueryService.CategoryMinorItem("생활가전", 1)))));

        // when & then
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].categoryMajor").value("테크·가전"))
                .andExpect(jsonPath("$.categories[0].categoryMinors[0].categoryMinor").value("생활가전"));
    }

    @Test
    void 카테고리별_프로젝트_목록을_PageResponse_형태로_반환한다() throws Exception {
        // given
        when(categoryQueryService.getProjectsByCategory(eq("테크·가전"), isNull(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        // when & then
        mockMvc.perform(get("/api/v1/categories/테크·가전/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 존재하지_않는_카테고리는_400을_반환한다() throws Exception {
        // given
        when(categoryQueryService.getProjectsByCategory(eq("없는분류"), isNull(), any(), any()))
                .thenThrow(new BusinessException(SearchErrorCode.INVALID_CATEGORY));

        // when & then
        mockMvc.perform(get("/api/v1/categories/없는분류/projects"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"));
    }
}

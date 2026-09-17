package com.fundit.search.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.search.application.category.CategoryQueryService;
import com.fundit.search.domain.SearchErrorCode;
import com.fundit.search.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class CategoryControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryQueryService categoryQueryService;

    @Test
    void 존재하지_않는_카테고리는_400을_반환한다() throws Exception {
        // given
        when(categoryQueryService.getProjectsByCategory(eq("없는분류"), isNull(), any(), any()))
                .thenThrow(new BusinessException(SearchErrorCode.INVALID_CATEGORY));

        // when
        var result = mockMvc.perform(get("/api/v1/categories/없는분류/projects"));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"));
    }

    @Test
    void page가_음수이면_400을_반환한다() throws Exception {
        // given
        // when
        var result = mockMvc.perform(get("/api/v1/categories/테크·가전/projects").param("page", "-1"));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}

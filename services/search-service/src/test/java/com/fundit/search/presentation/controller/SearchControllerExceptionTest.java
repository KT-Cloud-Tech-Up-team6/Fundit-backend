package com.fundit.search.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.search.application.live.LiveCardClient;
import com.fundit.search.application.search.PopularKeywordQueryService;
import com.fundit.search.application.search.ProjectSearchService;
import com.fundit.search.application.search.RecentKeywordService;
import com.fundit.search.application.search.SellerSearchService;
import com.fundit.search.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class SearchControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectSearchService projectSearchService;
    @MockitoBean
    private RecentKeywordService recentKeywordService;
    @MockitoBean
    private PopularKeywordQueryService popularKeywordQueryService;
    @MockitoBean
    private SellerSearchService sellerSearchService;
    @MockitoBean
    private LiveCardClient liveCardClient;

    @Test
    void 로그인_없이_최근_검색어를_조회하면_401을_반환한다() throws Exception {
        // given: 없음

        // when
        var result = mockMvc.perform(get("/api/v1/search/recent-keywords"));

        // then
        result.andExpect(status().isUnauthorized());
    }

    @Test
    void 상품_검색_page가_음수이면_400을_반환한다() throws Exception {
        // given: 없음

        // when
        var result = mockMvc.perform(get("/api/v1/search/projects")
                .param("keyword", "무선 이어폰")
                .param("page", "-1"));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 판매자_검색_size가_1_미만이면_400을_반환한다() throws Exception {
        // given: 없음

        // when
        var result = mockMvc.perform(get("/api/v1/search/sellers")
                .param("keyword", "프라이팬장인")
                .param("size", "0"));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}

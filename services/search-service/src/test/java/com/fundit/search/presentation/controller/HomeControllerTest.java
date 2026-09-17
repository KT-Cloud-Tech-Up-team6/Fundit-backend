package com.fundit.search.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.search.application.home.HomeFeedQueryService;
import com.fundit.search.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HomeFeedQueryService homeFeedQueryService;

    @Test
    void 홈피드는_content_형태로_반환된다() throws Exception {
        // given
        when(homeFeedQueryService.getHomeFeed(any())).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/v1/home/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void 홈_LIVE는_항상_빈_배열을_반환하는_스텁이다() throws Exception {
        // when
        var result = mockMvc.perform(get("/api/v1/home/lives"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }
}

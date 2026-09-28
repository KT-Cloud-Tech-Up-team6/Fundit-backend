package com.fundit.search.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.common.error.DependencyFailureException;
import com.fundit.search.application.home.HomeFeedQueryService;
import com.fundit.search.application.live.LiveCardClient;
import com.fundit.search.application.live.LiveCardClient.LiveCard;
import com.fundit.search.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    @MockitoBean
    private LiveCardClient liveCardClient;

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
    void 홈_LIVE는_live_service_배너를_그대로_내려준다() throws Exception {
        // given
        UUID liveId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(liveCardClient.findBanner()).thenReturn(List.of(new LiveCard(
                liveId, "캠핑 의자 라이브", "LIVE", projectId, "https://cdn/thumb.png",
                Instant.parse("2026-09-24T12:00:00Z"), 7, Instant.parse("2026-09-20T09:00:00Z"), null)));

        // when & then — FE가 LIVE 메인과 같은 카드 컴포넌트를 쓰므로 필드 이름이 계약이다
        mockMvc.perform(get("/api/v1/home/lives"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].liveId").value(liveId.toString()))
                .andExpect(jsonPath("$.content[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.content[0].introText").value("캠핑 의자 라이브"))
                .andExpect(jsonPath("$.content[0].status").value("LIVE"))
                .andExpect(jsonPath("$.content[0].thumbnailUrl").value("https://cdn/thumb.png"))
                .andExpect(jsonPath("$.content[0].likeCount").value(7))
                .andExpect(jsonPath("$.content[0].scheduledStartAt").exists());
    }

    @Test
    void live_service가_죽어도_홈은_200과_빈_LIVE_목록을_반환한다() throws Exception {
        // given — 홈은 피드와 LIVE가 한 화면이라 LIVE 하나로 홈 전체를 내리지 않는다
        when(liveCardClient.findBanner()).thenThrow(new DependencyFailureException(new RuntimeException("live down")));

        // when & then
        mockMvc.perform(get("/api/v1/home/lives"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }
}

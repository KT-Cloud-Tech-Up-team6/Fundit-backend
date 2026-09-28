package com.fundit.search.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.search.application.live.LiveCardClient;
import com.fundit.search.application.live.LiveCardClient.LiveCard;
import com.fundit.search.application.search.PopularKeywordQueryService;
import com.fundit.search.application.search.PopularKeywordQueryService.PopularKeywordItem;
import com.fundit.search.application.search.ProjectSearchService;
import com.fundit.search.application.search.RecentKeywordService;
import com.fundit.search.application.search.RecentKeywordService.RecentKeywordItem;
import com.fundit.search.application.search.SellerSearchService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** internal-api.key 고정 이유는 NotificationControllerTest와 동일. */
@WebMvcTest(SearchController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class SearchControllerTest {

    private static final String API_KEY = "test-only-internal-api-key";

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
    void 비로그인_상품_검색은_최근검색어_저장_없이_동작한다() throws Exception {
        // given
        when(projectSearchService.search(eq("무선 이어폰"), any(), any(), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        // when & then
        mockMvc.perform(get("/api/v1/search/projects").param("keyword", "무선 이어폰"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void 로그인_상품_검색은_X_User_Id를_memberId로_전달한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        when(projectSearchService.search(eq("캠핑 의자"), any(), any(), eq(memberId), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        // when & then
        mockMvc.perform(get("/api/v1/search/projects").param("keyword", "캠핑 의자")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isOk());
        verify(projectSearchService).search(eq("캠핑 의자"), any(), any(), eq(memberId), any());
    }

    @Test
    void 검색_LIVE_탭은_live_service_목록을_PageResponse로_반환한다() throws Exception {
        // given
        UUID liveId = UUID.randomUUID();
        LiveCard card = new LiveCard(liveId, "캠핑 의자 라이브", "SCHEDULED", UUID.randomUUID(),
                "https://cdn/thumb.png", Instant.parse("2026-09-25T11:00:00Z"), 3,
                Instant.parse("2026-09-20T09:00:00Z"), null);
        when(liveCardClient.findPublic(any()))
                .thenReturn(new PageImpl<>(List.of(card), PageRequest.of(0, 20), 1));

        // when & then
        mockMvc.perform(get("/api/v1/search/lives"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].liveId").value(liveId.toString()))
                .andExpect(jsonPath("$.content[0].introText").value("캠핑 의자 라이브"))
                .andExpect(jsonPath("$.content[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 판매자_탭_검색결과를_PageResponse_형태로_반환한다() throws Exception {
        // given
        when(sellerSearchService.search(eq("프라이팬장인"), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        // when & then
        mockMvc.perform(get("/api/v1/search/sellers").param("keyword", "프라이팬장인"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void 최근_검색어_목록을_조회한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();
        when(recentKeywordService.getRecentKeywords(eq(memberId), any()))
                .thenReturn(List.of(new RecentKeywordItem("무선 이어폰", Instant.parse("2026-09-16T21:00:00Z"))));

        // when & then
        mockMvc.perform(get("/api/v1/search/recent-keywords")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].keyword").value("무선 이어폰"));
    }

    @Test
    void 최근_검색어를_개별_삭제하면_204를_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/v1/search/recent-keywords/무선 이어폰")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isNoContent());
        verify(recentKeywordService).deleteKeyword(memberId, "무선 이어폰");
    }

    @Test
    void 최근_검색어를_전체_삭제하면_204를_반환한다() throws Exception {
        // given
        UUID memberId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/v1/search/recent-keywords")
                        .header("X-User-Id", memberId.toString())
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isNoContent());
        verify(recentKeywordService).deleteAllKeywords(memberId);
    }

    @Test
    void 인기_검색어를_조회한다() throws Exception {
        // given
        when(popularKeywordQueryService.getPopularKeywords())
                .thenReturn(List.of(new PopularKeywordItem(1, "무선 이어폰")));

        // when & then
        mockMvc.perform(get("/api/v1/search/popular-keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rank").value(1))
                .andExpect(jsonPath("$.content[0].keyword").value("무선 이어폰"));
    }
}

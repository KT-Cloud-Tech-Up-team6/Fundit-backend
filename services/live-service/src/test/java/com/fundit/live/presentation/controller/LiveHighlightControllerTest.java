package com.fundit.live.presentation.controller;

import com.fundit.common.auth.AuthHeaders;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.live.application.highlight.HighlightService;
import com.fundit.live.infrastructure.persistence.highlight.LiveHighlightJpaEntity;
import com.fundit.live.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LiveHighlightController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class LiveHighlightControllerTest {

    private static final String INTERNAL_KEY = "test-only-internal-api-key";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private HighlightService highlightService;

    private final UUID userId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    private LiveHighlightJpaEntity entity(String kind) {
        return LiveHighlightJpaEntity.builder()
                .id(1L).publicId(UUID.randomUUID()).sessionId(1L).kind(kind)
                .sceneLabel("DEMO").title("실시간 시연").startSec(320)
                .endSec("CLIP".equals(kind) ? 400 : null)
                .isPublic(false).generationStatus("COMPLETED").viewCount(12).clickCount(3).build();
    }

    @Test
    void 목록은_마커와_클립을_두_배열로_나눠_내려준다() throws Exception {
        // given — 화면이 타임라인과 쇼츠를 따로 그린다
        when(highlightService.findAll(any(), any())).thenReturn(List.of(
                entity(LiveHighlightJpaEntity.KIND_MARKER), entity(LiveHighlightJpaEntity.KIND_CLIP)));

        // when & then
        mockMvc.perform(get("/api/v1/lives/{liveId}/highlights", liveId)
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markers.length()").value(1))
                .andExpect(jsonPath("$.clips.length()").value(1));
    }

    @Test
    void 소비자_공개_목록은_인증_없이_조회된다() throws Exception {
        // given
        when(highlightService.findPublic(any())).thenReturn(List.of());

        // when & then
        mockMvc.perform(get("/api/v1/lives/{liveId}/highlights/public", liveId))
                .andExpect(status().isOk());
    }

    @Test
    void 통계에는_펀딩_전환_기여가_없다() throws Exception {
        // given — order 집계 주체가 미정이라 채울 수 없다.
        // 0을 내려주면 프론트가 실제 값으로 오해한다
        when(highlightService.findAll(any(), any())).thenReturn(List.of(entity("CLIP")));

        // when & then
        mockMvc.perform(get("/api/v1/lives/{liveId}/highlights/stats", liveId)
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].viewCount").value(12))
                .andExpect(jsonPath("$[0].fundingConversionCount").doesNotExist());
    }

    @Test
    void 공개_설정은_isPublic을_요구한다() throws Exception {
        // given & when & then — 값이 없으면 공개인지 비공개인지 알 수 없다
        mockMvc.perform(patch("/api/v1/lives/{liveId}/highlights/{hid}/visibility", liveId, UUID.randomUUID())
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 생성_요청은_202다() throws Exception {
        // given & when & then — 비동기다. 결과는 AI가 내부 경로로 밀어준다
        mockMvc.perform(post("/api/v1/lives/{liveId}/highlights", liveId)
                        .header(AuthHeaders.USER_ID, userId.toString())
                        .header(AuthHeaders.INTERNAL_API_KEY, INTERNAL_KEY))
                .andExpect(status().isAccepted());
    }

    @Test
    void 클릭_기록은_인증_없이_204다() throws Exception {
        // given & when & then — 전환 동선 추적용이다
        mockMvc.perform(post("/api/v1/lives/{liveId}/highlights/{hid}/click", liveId, UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }
}

package com.fundit.live.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.live.application.highlight.HighlightService;
import com.fundit.live.domain.ai.GenerationStatus;
import com.fundit.live.domain.highlight.HighlightKind;
import com.fundit.live.domain.highlight.LiveHighlight;
import com.fundit.live.domain.highlight.ProjectClip;
import com.fundit.live.domain.highlight.SceneLabel;
import com.fundit.live.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectClipController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class ProjectClipControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private HighlightService highlightService;

    @Test
    void 프로젝트_클립_목록은_인증_없이_방송_id와_함께_내려준다() throws Exception {
        // given — 여러 방송의 클립이 섞이므로 어느 방송 것인지 같이 준다
        UUID projectId = UUID.randomUUID();
        UUID liveId = UUID.randomUUID();
        LiveHighlight clip = LiveHighlight.builder()
                .id(1L).publicId(UUID.randomUUID()).sessionId(1L).kind(HighlightKind.CLIP)
                .sceneLabel(SceneLabel.DEMO).title("실시간 시연").startSec(320).endSec(400)
                .clipUrl("https://clip").thumbnailUrl("https://thumb")
                .isPublic(true).generationStatus(GenerationStatus.COMPLETED)
                .createdAt(Instant.parse("2026-09-10T12:00:00Z")).build();
        when(highlightService.findPublicClips(eq(projectId), any()))
                .thenReturn(new PageImpl<>(List.of(new ProjectClip(liveId, clip)), PageRequest.of(0, 20), 1));

        // when
        ResultActions result = mockMvc.perform(get("/api/v1/lives/highlights").param("projectId", projectId.toString()));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].liveId").value(liveId.toString()))
                .andExpect(jsonPath("$.content[0].thumbnailUrl").value("https://thumb"))
                .andExpect(jsonPath("$.content[0].createdAt").exists())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}

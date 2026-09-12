package com.fundit.project.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.project.application.media.MediaStorageClient;
import com.fundit.project.application.media.MediaUploadService;
import com.fundit.project.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaUploadService mediaUploadService;

    @Test
    void 업로드_주소를_발급하면_200과_uploadUrl_fileUrl을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(mediaUploadService.issueUploadUrl(any(), any(), anyString(), anyString(), anyLong()))
                .thenReturn(new MediaStorageClient.PresignedUpload("https://upload", "https://file"));

        // when & then
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/media/upload-url")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType("application/json")
                        .content("""
                                {"fileName":"cover.jpg","contentType":"image/jpeg","fileSize":1024}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://upload"))
                .andExpect(jsonPath("$.fileUrl").value("https://file"));
    }

    @Test
    void 소유자가_아니면_403을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(mediaUploadService.issueUploadUrl(any(), any(), anyString(), anyString(), anyLong()))
                .thenThrow(new BusinessException(CommonErrorCode.FORBIDDEN));

        // when & then
        mockMvc.perform(post("/api/v1/projects/" + projectId + "/media/upload-url")
                        .header("X-User-Id", sellerId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType("application/json")
                        .content("""
                                {"fileName":"cover.jpg","contentType":"image/jpeg","fileSize":1024}
                                """))
                .andExpect(status().isForbidden());
    }
}

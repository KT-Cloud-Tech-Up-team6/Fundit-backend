package com.fundit.member.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.member.application.wish.WishService;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.member.infrastructure.security.InternalEndpointConfig;
import com.fundit.member.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link WishControllerTest} 참고. */
@WebMvcTest(WishController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class, InternalEndpointConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class WishControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WishService wishService;

    @Test
    void page가_음수이면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/wishes")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void size가_0이면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/wishes")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void size가_최대값을_초과하면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/wishes")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 공개_id에_해당하는_프로젝트가_없으면_404를_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        doThrow(new BusinessException(CommonErrorCode.NOT_FOUND, "프로젝트를 찾을 수 없습니다."))
                .when(wishService).wish(accountId, publicId);

        // when & then
        mockMvc.perform(put("/api/v1/wishes/projects/" + publicId)
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 공개_id가_UUID_형식이_아니면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/wishes/projects/not-a-uuid")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 인증헤더_없이_공개_id로_찜_여부를_조회하면_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/wishes/projects/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}

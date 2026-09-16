package com.fundit.member.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.member.application.follow.FollowService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link FollowControllerTest} 참고. */
@WebMvcTest(FollowController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class, InternalEndpointConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class FollowControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FollowService followService;

    @Test
    void sellerId가_UUID가_아니면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(put("/api/v1/follows/not-a-uuid")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void page가_음수이면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/follows")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void size가_최대값을_초과하면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/follows")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }
}

package com.fundit.member.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.member.application.follow.FollowService;
import com.fundit.member.infrastructure.persistence.follow.FollowView;
import com.fundit.member.infrastructure.security.InternalEndpointConfig;
import com.fundit.member.presentation.GlobalExceptionHandler;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FollowController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class, InternalEndpointConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class FollowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FollowService followService;

    @Test
    void 팔로우하면_following_true를_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        // when & then
        mockMvc.perform(put("/api/v1/follows/" + sellerId)
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value(sellerId.toString()))
                .andExpect(jsonPath("$.following").value(true));
        verify(followService).follow(accountId, sellerId);
    }

    @Test
    void 언팔로우하면_204를_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/v1/follows/" + sellerId)
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key"))
                .andExpect(status().isNoContent());
        verify(followService).unfollow(accountId, sellerId);
    }

    @Test
    void 팔로우_목록을_조회한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        when(followService.getFollows(accountId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(new FollowView(sellerId, "홍길동", "길동", Instant.now()))));

        // when & then
        mockMvc.perform(get("/api/v1/follows")
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sellerId").value(sellerId.toString()))
                .andExpect(jsonPath("$.content[0].sellerName").value("홍길동"));
    }

    @Test
    void 인증헤더_없이_팔로우하면_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(put("/api/v1/follows/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}

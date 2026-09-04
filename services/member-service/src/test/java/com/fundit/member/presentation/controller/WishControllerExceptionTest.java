package com.fundit.member.presentation.controller;

import com.fundit.member.application.wish.WishService;
import com.fundit.member.infrastructure.security.CurrentMemberArgumentResolver;
import com.fundit.member.infrastructure.security.WebConfig;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link WishControllerTest} 참고. */
@WebMvcTest(WishController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, WebConfig.class})
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
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void size가_0이면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/wishes")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void size가_최대값을_초과하면_400을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/wishes")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }
}

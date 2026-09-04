package com.fundit.member.presentation.controller;

import com.fundit.member.application.wish.WishService;
import com.fundit.member.infrastructure.security.CurrentMemberArgumentResolver;
import com.fundit.member.infrastructure.security.WebConfig;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WishController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, WebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class WishControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WishService wishService;

    @Test
    void 찜을_등록하면_wished_true를_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();

        // when & then
        mockMvc.perform(put("/api/v1/wishes/1").header("X-Account-Id", accountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(1))
                .andExpect(jsonPath("$.wished").value(true));
        verify(wishService).wish(accountId, 1L);
    }

    @Test
    void 찜을_해제하면_204를_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/v1/wishes/1").header("X-Account-Id", accountId.toString()))
                .andExpect(status().isNoContent());
        verify(wishService).unwish(accountId, 1L);
    }

    @Test
    void 인증헤더_없이_찜을_등록하면_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(put("/api/v1/wishes/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 찜_목록을_페이지네이션_형태로_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        when(wishService.getWishes(accountId, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(new WishService.WishItem(1L, "프로젝트A", null, null))));

        // when & then
        mockMvc.perform(get("/api/v1/wishes").header("X-Account-Id", accountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].projectTitle").value("프로젝트A"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}

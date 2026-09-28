package com.fundit.member.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.member.application.member.MemberQueryService;
import com.fundit.member.infrastructure.security.InternalEndpointConfig;
import com.fundit.member.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 예외 흐름은 {@link InternalMemberControllerExceptionTest} 참고. */
@WebMvcTest(InternalMemberController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class, InternalEndpointConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class InternalMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberQueryService memberQueryService;

    @Test
    void 닉네임_일괄_조회는_회원별_닉네임을_돌려준다() throws Exception {
        // given
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(memberQueryService.findNicknames(List.of(a, b))).thenReturn(List.of(
                new MemberQueryService.MemberNickname(a, "쓱쓱생활연구소")));

        // when & then — 없는 id(b)는 응답에서 빠진다
        mockMvc.perform(get("/internal/v1/members/nicknames")
                        .param("ids", a.toString(), b.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].memberId").value(a.toString()))
                .andExpect(jsonPath("$[0].nickname").value("쓱쓱생활연구소"));
    }
}

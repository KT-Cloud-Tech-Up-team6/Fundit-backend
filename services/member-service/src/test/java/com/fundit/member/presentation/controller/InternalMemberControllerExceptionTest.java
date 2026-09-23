package com.fundit.member.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link InternalMemberControllerTest} 참고. */
@WebMvcTest(InternalMemberController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class, InternalEndpointConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class InternalMemberControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberQueryService memberQueryService;

    @Test
    void 내부_키가_없으면_401이다() throws Exception {
        // when & then — 게이트웨이 라우트엔 없지만 서비스 포트로 직접 들어오는 호출도 막아야 한다
        mockMvc.perform(get("/internal/v1/members/nicknames").param("ids", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 상한을_넘으면_400이다() throws Exception {
        // given
        when(memberQueryService.findNicknames(anyList()))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_INPUT));

        // when & then
        mockMvc.perform(get("/internal/v1/members/nicknames")
                        .param("ids", UUID.randomUUID().toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key"))
                .andExpect(status().isBadRequest());
    }
}

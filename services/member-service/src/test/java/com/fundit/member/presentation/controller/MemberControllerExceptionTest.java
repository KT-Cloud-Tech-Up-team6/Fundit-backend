package com.fundit.member.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.member.application.member.MemberQueryService;
import com.fundit.member.application.member.MemberSignupService;
import com.fundit.member.infrastructure.security.CurrentMemberArgumentResolver;
import com.fundit.member.infrastructure.security.WebConfig;
import com.fundit.member.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link MemberControllerTest} 참고. */
@WebMvcTest(MemberController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, WebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class MemberControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberSignupService memberSignupService;
    @MockitoBean
    private MemberQueryService memberQueryService;

    @Test
    void 필수약관에_동의하지_않으면_400을_반환한다() throws Exception {
        // given
        when(memberSignupService.signup(any()))
                .thenThrow(new BusinessException(CommonErrorCode.INVALID_INPUT, "필수 약관에 동의해야 합니다."));

        // when & then
        mockMvc.perform(post("/api/v1/members")
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "name": "홍길동",
                                  "phoneNumber": "01012345678",
                                  "agreedTerms": ["MARKETING"]
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 주소를_일부_필드만_입력하면_400을_반환한다() throws Exception {
        // when & then (recipientName만 있고 나머지 필수 필드 누락)
        mockMvc.perform(post("/api/v1/members")
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "name": "홍길동",
                                  "phoneNumber": "01012345678",
                                  "agreedTerms": ["SERVICE_USE", "PRIVACY", "AGE_OVER_14"],
                                  "address": {"recipientName": "홍길동"}
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 잘못된_내부API키로_요청하면_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/members")
                        .header("X-Internal-Api-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": "%s", "name": "홍길동", "phoneNumber": "01012345678", "agreedTerms": ["SERVICE_USE"]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 이미_생성된_회원이면_409를_반환한다() throws Exception {
        // given
        when(memberSignupService.signup(any()))
                .thenThrow(new BusinessException(CommonErrorCode.CONFLICT, "이미 생성된 회원 프로필입니다."));

        // when & then
        mockMvc.perform(post("/api/v1/members")
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "name": "홍길동",
                                  "phoneNumber": "01012345678",
                                  "agreedTerms": ["SERVICE_USE", "PRIVACY", "AGE_OVER_14"]
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict());
    }

    @Test
    void 존재하지_않는_회원이면_404를_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        when(memberQueryService.getMe(accountId)).thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND));

        // when & then
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/members/me").header("X-Account-Id", accountId.toString()))
                .andExpect(status().isNotFound());
    }
}

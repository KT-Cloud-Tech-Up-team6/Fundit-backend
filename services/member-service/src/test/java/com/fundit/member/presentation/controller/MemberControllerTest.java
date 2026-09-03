package com.fundit.member.presentation.controller;

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

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InternalApiKeyFilter/CurrentMemberArgumentResolver가 실제로 MockMvc 필터 체인·
 * 인자 리졸버로 붙는지까지 함께 검증한다(WebConfig import).
 */
@WebMvcTest(MemberController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, WebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberSignupService memberSignupService;
    @MockitoBean
    private MemberQueryService memberQueryService;

    @Test
    void 올바른_내부API키로_요청하면_회원프로필을_생성한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        when(memberSignupService.signup(any())).thenReturn(
                new MemberSignupService.SignupResult(accountId, Instant.now()));

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
                                """.formatted(accountId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(accountId.toString()))
                .andExpect(jsonPath("$.isSeller").value(true))
                .andExpect(jsonPath("$.isBuyer").value(true));
    }

    @Test
    void 내부API키가_없으면_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": "%s", "name": "홍길동", "phoneNumber": "01012345678", "agreedTerms": ["SERVICE_USE"]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void X_Account_Id_헤더가_있으면_내_프로필을_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        when(memberQueryService.getMe(accountId)).thenReturn(
                new MemberQueryService.MemberProfile(accountId, "홍길동", null, "01012345678"));

        // when & then
        mockMvc.perform(get("/api/v1/members/me").header("X-Account-Id", accountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.currentMode").doesNotExist());
    }

    @Test
    void X_Account_Id_헤더가_없으면_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized());
    }
}

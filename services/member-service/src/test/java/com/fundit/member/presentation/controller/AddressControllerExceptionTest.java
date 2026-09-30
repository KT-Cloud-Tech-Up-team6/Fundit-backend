package com.fundit.member.presentation.controller;

import com.fundit.member.application.address.AddressService;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.member.infrastructure.security.InternalEndpointConfig;
import com.fundit.member.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link AddressControllerTest} 참고. */
@WebMvcTest(AddressController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class, InternalEndpointConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class AddressControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressService addressService;

    @Test
    void 필수_필드가_누락되면_400을_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();

        // when & then (recipientName 누락)
        mockMvc.perform(post("/api/v1/addresses")
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phoneNumber": "01012345678",
                                  "zipcode": "12345",
                                  "addressLine1": "테헤란로 1"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    // QA-061·062·063(#209)
    @ParameterizedTest
    @ValueSource(strings = {"abc", "010123", "00000000000"})
    void 연락처가_휴대폰_번호_형식이_아니면_등록과_수정_모두_400을_반환한다(String phoneNumber) throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        String body = """
                {"recipientName": "홍길동", "phoneNumber": "%s", "zipcode": "12345", "addressLine1": "테헤란로 1"}
                """.formatted(phoneNumber);

        // when & then
        mockMvc.perform(post("/api/v1/addresses")
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(put("/api/v1/addresses/1")
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", "test-only-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}

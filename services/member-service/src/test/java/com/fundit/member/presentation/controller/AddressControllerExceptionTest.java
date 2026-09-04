package com.fundit.member.presentation.controller;

import com.fundit.member.application.address.AddressService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link AddressControllerTest} 참고. */
@WebMvcTest(AddressController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, WebConfig.class})
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
                        .header("X-Account-Id", accountId.toString())
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
}

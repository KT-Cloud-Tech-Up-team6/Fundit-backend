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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AddressController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, WebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressService addressService;

    @Test
    void 본인의_배송지_목록을_조회한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        when(addressService.list(accountId)).thenReturn(List.of(
                new AddressService.AddressItem(1L, "홍길동", "01012345678", "12345", "테헤란로 1", null, true)));

        // when & then
        mockMvc.perform(get("/api/v1/addresses").header("X-Account-Id", accountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipientName").value("홍길동"))
                .andExpect(jsonPath("$[0].isDefault").value(true));
    }

    @Test
    void 배송지를_등록한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        when(addressService.register(eq(accountId), any())).thenReturn(
                new AddressService.AddressItem(1L, "홍길동", "01012345678", "12345", "테헤란로 1", null, true));

        // when & then
        mockMvc.perform(post("/api/v1/addresses")
                        .header("X-Account-Id", accountId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientName": "홍길동",
                                  "phoneNumber": "01012345678",
                                  "zipcode": "12345",
                                  "addressLine1": "테헤란로 1",
                                  "isDefault": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientName").value("홍길동"))
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    @Test
    void 인증헤더_없이_배송지목록을_조회하면_401을_반환한다() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().isUnauthorized());
    }
}

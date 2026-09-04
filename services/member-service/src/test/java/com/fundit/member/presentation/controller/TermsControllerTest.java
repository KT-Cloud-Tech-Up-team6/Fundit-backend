package com.fundit.member.presentation.controller;

import com.fundit.member.application.terms.TermsService;
import com.fundit.member.infrastructure.terms.TermsCatalog;
import com.fundit.member.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TermsController.class)
@Import(GlobalExceptionHandler.class)
class TermsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TermsService termsService;

    @Test
    void 인증없이_약관목록을_조회할_수_있다() throws Exception {
        // given
        when(termsService.getTerms()).thenReturn(List.of(
                new TermsCatalog.Terms("SERVICE_USE", "서비스 이용약관", "내용", true, "1.0")));

        // when & then
        mockMvc.perform(get("/api/v1/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("SERVICE_USE"))
                .andExpect(jsonPath("$[0].required").value(true));
    }
}

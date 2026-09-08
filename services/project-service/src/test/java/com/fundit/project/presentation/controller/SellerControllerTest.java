package com.fundit.project.presentation.controller;

import com.fundit.project.application.project.SellerService;
import com.fundit.project.infrastructure.security.CurrentAdminArgumentResolver;
import com.fundit.project.infrastructure.security.CurrentMemberArgumentResolver;
import com.fundit.project.infrastructure.security.WebConfig;
import com.fundit.project.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SellerController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, CurrentAdminArgumentResolver.class, WebConfig.class})
class SellerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SellerService sellerService;

    @Test
    void 판매자_정보를_조회하면_200을_반환한다() throws Exception {
        // given
        UUID sellerId = UUID.randomUUID();
        var view = new SellerService.SellerProfileView(sellerId, "SOLE",
                List.of(new SellerService.PastProjectView(UUID.randomUUID(), "이전 프로젝트", "SUCCEEDED")));
        when(sellerService.getProfile(sellerId)).thenReturn(view);

        // when & then
        mockMvc.perform(get("/api/v1/sellers/" + sellerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessType").value("SOLE"))
                .andExpect(jsonPath("$.pastProjects[0].status").value("SUCCEEDED"));
    }
}

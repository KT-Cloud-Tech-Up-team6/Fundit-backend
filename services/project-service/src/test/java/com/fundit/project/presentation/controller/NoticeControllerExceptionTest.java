package com.fundit.project.presentation.controller;

import com.fundit.project.application.notice.NoticeService;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link NoticeControllerTest} 참고. */
@WebMvcTest(NoticeController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class, CurrentAdminArgumentResolver.class, WebConfig.class})
class NoticeControllerExceptionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoticeService noticeService;

    @Test
    void 인증헤더_없이_새소식_등록시_401을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + UUID.randomUUID() + "/notices")
                        .contentType("application/json")
                        .content("{\"noticeType\":\"FAQ\",\"title\":\"제목\",\"content\":\"내용\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noticeType이_화이트리스트에_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/projects/" + UUID.randomUUID() + "/notices")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{\"noticeType\":\"NOT_A_TYPE\",\"title\":\"제목\",\"content\":\"내용\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sort값이_올바르지_않으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID() + "/notices").param("sort", "WRONG"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 댓글_내용이_500자를_초과하면_400을_반환한다() throws Exception {
        String tooLong = "a".repeat(501);
        mockMvc.perform(post("/api/v1/notices/1/comments")
                        .header("X-Account-Id", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{\"content\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
    }
}

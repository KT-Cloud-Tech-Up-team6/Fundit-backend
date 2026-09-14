package com.fundit.order.presentation.controller;

import com.fundit.order.application.supporter.SupporterActivityService;
import com.fundit.order.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ORDER-001 — 인증 불필요 API라 CurrentMember 리졸버 없이 컨트롤러만 슬라이스 테스트한다. */
@WebMvcTest(SupporterActivityController.class)
@Import(GlobalExceptionHandler.class)
class SupporterActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupporterActivityService supporterActivityService;

    @Test
    void 서포터_활동_목록을_상대시간과_함께_조회한다() throws Exception {
        // given — toRelativeTime의 방금전/분전/시간전/일전 분기를 모두 지나가게 한다
        List<SupporterActivityService.SupporterActivity> activities = List.of(
                new SupporterActivityService.SupporterActivity("구매자", 10_000L, Instant.now().minusSeconds(10)),
                new SupporterActivityService.SupporterActivity("구매자", 5_000L, Instant.now().minus(5, ChronoUnit.MINUTES)),
                new SupporterActivityService.SupporterActivity("구매자", 3_000L, Instant.now().minus(3, ChronoUnit.HOURS)),
                new SupporterActivityService.SupporterActivity("익명", null, Instant.now().minus(2, ChronoUnit.DAYS)));
        when(supporterActivityService.list(eq(1L), any())).thenReturn(new PageImpl<>(activities));

        // when & then
        mockMvc.perform(get("/api/v1/projects/1/supporters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].relativeTime").value("방금 전"))
                .andExpect(jsonPath("$.content[1].relativeTime").value("5분 전"))
                .andExpect(jsonPath("$.content[2].relativeTime").value("3시간 전"))
                .andExpect(jsonPath("$.content[3].relativeTime").value("2일 전"))
                .andExpect(jsonPath("$.content[3].amount").doesNotExist());
    }
}

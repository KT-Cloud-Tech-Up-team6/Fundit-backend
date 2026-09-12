package com.fundit.notification.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.notification.application.live.LiveNotifyService;
import com.fundit.notification.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LiveNotifyController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class LiveNotifyControllerTest {

    private static final String API_KEY = "test-only-internal-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LiveNotifyService liveNotifyService;

    @Test
    void 알림을_신청하면_notifying_true를_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        UUID liveId = UUID.randomUUID();

        // when & then
        mockMvc.perform(put("/api/v1/lives/" + liveId + "/notify")
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifying").value(true));
        verify(liveNotifyService).requestNotify(liveId, accountId);
    }

    @Test
    void 알림을_해제하면_notifying_false를_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        UUID liveId = UUID.randomUUID();

        // when & then
        mockMvc.perform(delete("/api/v1/lives/" + liveId + "/notify")
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifying").value(false));
        verify(liveNotifyService).cancelNotify(liveId, accountId);
    }
}

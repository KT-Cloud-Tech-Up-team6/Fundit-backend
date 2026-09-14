package com.fundit.notification.presentation.controller;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.notification.application.notification.NotificationService;
import com.fundit.notification.application.setting.NotificationSettingService;
import com.fundit.notification.presentation.GlobalExceptionHandler;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 정상 흐름은 {@link NotificationControllerTest} 참고. */
@WebMvcTest(NotificationController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class NotificationControllerExceptionTest {

    private static final String API_KEY = "test-only-internal-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private NotificationSettingService notificationSettingService;

    /** 정의되지 않은 notifType은 Jackson이 거르고 AbstractGlobalExceptionHandler가 400으로 매핑한다 — 검증 분기가 따로 없다. */
    @Test
    void 정의되지_않은_notifType이면_400을_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();

        // when & then
        mockMvc.perform(put("/api/v1/notification-settings")
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notifType\":\"NOPE\",\"enabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_INPUT.name()));
    }

    @Test
    void enabled가_누락되면_400을_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();

        // when & then
        mockMvc.perform(put("/api/v1/notification-settings")
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notifType\":\"SHIPPING_UPDATE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_INPUT.name()));
    }

    @Test
    void page가_음수이면_400을_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();

        // when & then
        mockMvc.perform(get("/api/v1/notifications").param("page", "-1")
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_INPUT.name()));
    }

    @Test
    void size가_상한을_넘으면_400을_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();

        // when & then
        mockMvc.perform(get("/api/v1/notifications").param("size", "101")
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 없거나_타인의_알림을_읽음_처리하면_404를_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        when(notificationService.markRead(any(), any()))
                .thenThrow(new BusinessException(CommonErrorCode.NOT_FOUND, "알림을 찾을 수 없습니다."));

        // when & then
        mockMvc.perform(patch("/api/v1/notifications/9001/read")
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.NOT_FOUND.name()));
    }

    /** X-User-Id를 실었는데 내부 키가 없으면 InternalGatewaySecretFilter가 401로 막는다 — 헤더 위조 방어선. */
    @Test
    void 내부_키_없이_사용자_헤더만_보내면_401을_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();

        // when & then
        mockMvc.perform(get("/api/v1/notifications").header("X-User-Id", accountId.toString()))
                .andExpect(status().isUnauthorized());
    }
}

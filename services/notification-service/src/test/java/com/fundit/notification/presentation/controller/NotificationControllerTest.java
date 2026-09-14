package com.fundit.notification.presentation.controller;

import com.fundit.common.webmvc.auth.CommonWebConfig;
import com.fundit.notification.application.notification.NotificationService;
import com.fundit.notification.application.notification.NotificationService.NotificationItem;
import com.fundit.notification.application.setting.NotificationSettingService;
import com.fundit.notification.infrastructure.persistence.notification.NotifType;
import com.fundit.notification.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 예외 흐름은 {@link NotificationControllerExceptionTest} 참고.
 *
 * <p>InternalEndpointConfig는 import하지 않는다 — 이 서비스에는 내부 전용 엔드포인트가 없고
 * CommonWebConfig가 ObjectProvider로 받으므로 빈이 없어도 된다.
 * internal-api.key를 고정하는 이유: profiles.active=local이 gitignore된 application-local.yml을
 * 가리켜서, CI 체크아웃 트리에 그 파일이 없으면 컨텍스트 로딩이 PlaceholderResolutionException으로 죽는다.
 */
@WebMvcTest(NotificationController.class)
@Import({GlobalExceptionHandler.class, CommonWebConfig.class})
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class NotificationControllerTest {

    private static final String API_KEY = "test-only-internal-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private NotificationSettingService notificationSettingService;

    @Test
    void 알림_목록은_PageResponse_형태로_반환된다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        var item = new NotificationItem(9001L, NotifType.LIVE_START, "「무선 이어폰 프로젝트」 LIVE가 시작됐어요",
                "/live/abc", null, Instant.parse("2026-09-03T10:15:00Z"));
        when(notificationService.getNotifications(any(), any()))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1));

        // when & then
        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].notificationId").value(9001))
                .andExpect(jsonPath("$.content[0].notifType").value("LIVE_START"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 읽음_처리하면_알림ID와_읽은_시각을_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        when(notificationService.markRead(9001L, accountId)).thenReturn(Instant.parse("2026-09-03T10:20:00Z"));

        // when & then
        mockMvc.perform(patch("/api/v1/notifications/9001/read")
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(9001))
                .andExpect(jsonPath("$.readAt").exists());
    }

    @Test
    void 안읽음_개수를_조회하면_unreadCount를_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();
        when(notificationService.countUnread(accountId)).thenReturn(7L);

        // when & then
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(7));
    }

    @Test
    void 수신설정을_변경하면_saved_true를_반환한다() throws Exception {
        // given
        UUID accountId = UUID.randomUUID();

        // when & then
        mockMvc.perform(put("/api/v1/notification-settings")
                        .header("X-User-Id", accountId.toString())
                        .header("X-Internal-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notifType\":\"SHIPPING_UPDATE\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));
        verify(notificationSettingService).setEnabled(accountId, NotifType.SHIPPING_UPDATE, false);
    }
}

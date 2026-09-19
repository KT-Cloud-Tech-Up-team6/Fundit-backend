package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.domain.session.LiveStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LiveSettingsServiceUnitTest {

    @Mock private LiveSessionRepository sessionRepository;
    @InjectMocks private LiveSettingsService liveSettingsService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID liveId = UUID.randomUUID();

    @Test
    void 예정시각을_넣으면_SCHEDULED가_된다() {
        // given
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.create(1L, UUID.randomUUID())));
        given(sessionRepository.save(any(LiveSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        LiveSession updated = liveSettingsService.update(sellerId, liveId, "테크·가전", "생활가전",
                "소개", null, Instant.parse("2026-09-10T11:00:00Z"));

        // then
        assertThat(updated.getStatus()).isEqualTo(LiveStatus.SCHEDULED);
        assertThat(updated.getCategoryMajor()).isEqualTo("테크·가전");
        assertThat(updated.getIntroText()).isEqualTo("소개");
    }

    @Test
    void 예정시각_없이_저장하면_DRAFT로_남는다() {
        // given — 임시저장이 곧 DRAFT다
        given(sessionRepository.findOwned(liveId, sellerId))
                .willReturn(Optional.of(LiveSession.create(1L, UUID.randomUUID())));
        given(sessionRepository.save(any(LiveSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        LiveSession updated = liveSettingsService.update(sellerId, liveId, null, null, "소개만", null, null);

        // then
        assertThat(updated.getStatus()).isEqualTo(LiveStatus.DRAFT);
    }

}

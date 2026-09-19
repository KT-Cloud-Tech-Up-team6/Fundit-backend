package com.fundit.live.application.chat;

import com.fundit.common.error.BusinessException;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.chat.ChatMessageJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChatIngestServiceUnitExceptionTest {

    @Mock private ChatMessageJpaRepository chatMessageRepository;
    @Mock private LiveSessionJpaRepository sessionRepository;

    @InjectMocks private ChatIngestService chatIngestService;

    @Test
    void 모르는_채팅방이면_404다() {
        // given
        given(sessionRepository.findByIvsChatRoomArn("arn:unknown")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatIngestService.ingest("arn:unknown", "m", UUID.randomUUID(), "x",
                Instant.now())).isInstanceOf(BusinessException.class);
    }
}

package com.fundit.live.application.session;

import com.fundit.common.error.DependencyFailureException;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * IVS 실패 시 저장한 ERROR 상태가 <b>실제로 커밋되는지</b> 확인한다.
 *
 * <p>단위 테스트로는 잡히지 않는다 — {@code verify(save)}는 호출만 보고 커밋은 안 본다.
 * {@code DependencyFailureException}이 RuntimeException이라 기본 롤백 대상이었고,
 * {@code noRollbackFor}가 없으면 저장한 ERROR가 사라져 판매자 화면이 실패 사유를 못 본다.
 *
 * <p>클래스에 @Transactional을 걸지 않는다 — 서비스가 커밋한 결과를 별도로 읽어야 한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "internal-api.key=test-only-internal-api-key")
class LiveStreamServiceIntegrationExceptionTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired private LiveStreamService liveStreamService;
    @Autowired private LiveSessionRepository sessionRepository;
    @Autowired private LiveSessionJpaRepository sessionJpaRepository;
    @Autowired private LiveChannelJpaRepository channelRepository;

    @MockitoBean private IvsClient ivsClient;

    private UUID sellerId;
    private UUID liveId;

    @BeforeEach
    void setUp() {
        sellerId = UUID.randomUUID();
        Long channelId = channelRepository.save(LiveChannelJpaEntity.builder()
                .sellerId(sellerId).ivsChannelArn("arn")
                .ivsIngestEndpoint("rtmps://i").ivsPlaybackUrl("https://p").active(true).build()).getId();
        liveId = sessionRepository.save(LiveSession.create(channelId, UUID.randomUUID())).getPublicId();
    }

    @Test
    void IVS_실패로_저장한_ERROR가_롤백되지_않는다() {
        // given
        given(ivsClient.createChatRoom(anyString())).willThrow(new IllegalStateException("IVS 장애"));

        // when
        assertThatThrownBy(() -> liveStreamService.start(sellerId, liveId))
                .isInstanceOf(DependencyFailureException.class);

        // then — 별도로 다시 읽는다. 롤백됐다면 DRAFT로 남아 있다.
        var saved = sessionJpaRepository.findByPublicId(liveId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LiveStatus.ERROR);
        assertThat(saved.getErrorDetail()).contains("채팅방 생성 실패");
        assertThat(saved.getErrorOccurredAt()).isNotNull();
    }
}

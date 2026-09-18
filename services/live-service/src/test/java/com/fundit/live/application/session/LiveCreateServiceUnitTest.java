package com.fundit.live.application.session;

import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.application.project.ProjectOwnershipClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveCreateServiceUnitTest {

    @Mock private LiveSessionRepository sessionRepository;
    @Mock private LiveChannelJpaRepository channelRepository;
    @Mock private ProjectOwnershipClient projectOwnershipClient;
    @Mock private IvsClient ivsClient;

    @InjectMocks private LiveCreateService liveCreateService;

    private final UUID sellerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @Test
    void 본인_소유_프로젝트면_DRAFT로_생성된다() {
        // given
        given(projectOwnershipClient.findSellerId(projectId)).willReturn(Optional.of(sellerId));
        given(channelRepository.findBySellerId(sellerId))
                .willReturn(Optional.of(LiveChannelJpaEntity.builder().id(7L).sellerId(sellerId).build()));
        given(sessionRepository.save(any(LiveSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        LiveSession created = liveCreateService.create(sellerId, projectId);

        // then
        assertThat(created.getStatus()).isEqualTo(LiveStatus.DRAFT);
        assertThat(created.getChannelId()).isEqualTo(7L);
        assertThat(created.getProjectId()).isEqualTo(projectId);
    }

    @Test
    void 채널이_이미_있으면_다시_프로비저닝하지_않는다() {
        // given — 판매자당 채널 1개라 최초 1회만 IVS를 부른다
        given(projectOwnershipClient.findSellerId(projectId)).willReturn(Optional.of(sellerId));
        given(channelRepository.findBySellerId(sellerId))
                .willReturn(Optional.of(LiveChannelJpaEntity.builder().id(7L).sellerId(sellerId).build()));
        given(sessionRepository.save(any(LiveSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        liveCreateService.create(sellerId, projectId);

        // then
        verify(ivsClient, never()).createChannel(anyString());
    }

    @Test
    void 채널이_없으면_프로비저닝해서_저장한다() {
        // given
        given(projectOwnershipClient.findSellerId(projectId)).willReturn(Optional.of(sellerId));
        given(channelRepository.findBySellerId(sellerId)).willReturn(Optional.empty());
        given(ivsClient.createChannel(sellerId.toString()))
                .willReturn(new IvsClient.Channel("arn", "rtmps://ingest", "https://play", "key-ref"));
        given(channelRepository.save(any(LiveChannelJpaEntity.class)))
                .willAnswer(inv -> LiveChannelJpaEntity.builder().id(9L).sellerId(sellerId).build());
        given(sessionRepository.save(any(LiveSession.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        LiveSession created = liveCreateService.create(sellerId, projectId);

        // then
        assertThat(created.getChannelId()).isEqualTo(9L);
        verify(ivsClient).createChannel(sellerId.toString());
    }
}

package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.application.ivs.IvsClient;
import com.fundit.live.application.project.ProjectOwnershipClient;
import com.fundit.live.domain.session.LiveSession;
import com.fundit.live.domain.session.LiveSessionRepository;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaEntity;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * LIVE 생성(요구사항정의서 6.1.4). 본인 소유 프로젝트인지 project-service에 물어본 뒤
 * {@code DRAFT} 상태로 만든다.
 *
 * <p>채널이 없으면 함께 프로비저닝한다 — 판매자당 1개라 최초 1회만 실제 호출이 일어난다.
 */
@Service
@RequiredArgsConstructor
public class LiveCreateService {

    private final LiveSessionRepository sessionRepository;
    private final LiveChannelJpaRepository channelRepository;
    private final ProjectOwnershipClient projectOwnershipClient;
    private final IvsClient ivsClient;

    @Transactional
    public LiveSession create(UUID sellerId, UUID projectId) {
        UUID ownerId = projectOwnershipClient.findSellerId(projectId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND, "존재하지 않는 프로젝트입니다."));
        if (!ownerId.equals(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "본인 소유 프로젝트가 아닙니다.");
        }

        LiveChannelJpaEntity channel = channelRepository.findBySellerId(sellerId)
                .orElseGet(() -> provisionChannel(sellerId));

        return sessionRepository.save(LiveSession.create(channel.getId(), projectId));
    }

    private LiveChannelJpaEntity provisionChannel(UUID sellerId) {
        IvsClient.Channel channel = ivsClient.createChannel(sellerId.toString());
        return channelRepository.save(LiveChannelJpaEntity.builder()
                .sellerId(sellerId)
                .ivsChannelArn(channel.arn())
                .ivsIngestEndpoint(channel.ingestEndpoint())
                .ivsPlaybackUrl(channel.playbackUrl())
                .ivsStreamKeyRef(channel.streamKeyRef())
                .active(true)
                .build());
    }
}

package com.fundit.live.application.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.live.domain.session.LiveStatus;
import com.fundit.live.infrastructure.persistence.channel.LiveChannelJpaRepository;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaEntity;
import com.fundit.live.infrastructure.persistence.session.LiveSessionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** LIVE 시청 정보(요구사항정의서 11.2.4)와 다시보기(11.4.4). 둘 다 비인증이다 — 방송 자체가 공개다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LivePlaybackService {

    private final LiveSessionJpaRepository sessionRepository;
    private final LiveChannelJpaRepository channelRepository;

    /**
     * 종료된 방송을 요청하면 <b>다시보기 정보로 자동 전환</b>해 응답한다 —
     * 클라이언트가 404를 받고 VOD를 따로 재요청하지 않아도 된다(요구사항정의서 11.2.4).
     */
    public Playback playback(UUID liveId) {
        LiveSessionJpaEntity session = load(liveId);
        if (session.getStatus() == LiveStatus.ENDED) {
            return vodOf(session);
        }
        if (session.getStatus() != LiveStatus.LIVE) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "진행 중인 방송이 아닙니다.");
        }
        String playbackUrl = channelRepository.findById(session.getChannelId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND))
                .getIvsPlaybackUrl();
        return new Playback(session.getPublicId(), "LIVE", playbackUrl, session.getProjectId(),
                session.getLikeCount(), null);
    }

    public Playback vod(UUID liveId) {
        return vodOf(load(liveId));
    }

    private Playback vodOf(LiveSessionJpaEntity session) {
        if (session.getVodUrl() == null) {
            // 인코딩이 끝나기 전이다. 404로 뭉개면 "없는 방송"과 구분되지 않는다.
            throw new BusinessException(CommonErrorCode.CONFLICT, "다시보기가 아직 준비되지 않았습니다.");
        }
        return new Playback(session.getPublicId(), "VOD", session.getVodUrl(), session.getProjectId(),
                session.getLikeCount(), session.getVodReadyAt());
    }

    private LiveSessionJpaEntity load(UUID liveId) {
        LiveSessionJpaEntity session = sessionRepository.findByPublicId(liveId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (session.getStatus() == LiveStatus.DRAFT) {
            // 설정이 끝나지 않은 방송은 존재 자체를 알리지 않는다.
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return session;
    }

    /** {@code type}은 LIVE 또는 VOD. 클라이언트가 재생기를 고르는 기준이다. */
    public record Playback(UUID liveId, String type, String playbackUrl, UUID projectId,
                           int likeCount, Instant vodReadyAt) {
    }
}

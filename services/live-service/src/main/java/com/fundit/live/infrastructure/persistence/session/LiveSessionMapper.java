package com.fundit.live.infrastructure.persistence.session;

import com.fundit.live.domain.session.LiveSession;

/**
 * 도메인 ↔ JpaEntity 변환. package-private이라 이 패키지 밖에서는 JpaEntity를 직접 다룰 수 없다
 * (persistence-convention.md 1번).
 */
class LiveSessionMapper {

    private LiveSessionMapper() {
    }

    static LiveSession toDomain(LiveSessionJpaEntity entity) {
        return LiveSession.builder()
                .id(entity.getId())
                .publicId(entity.getPublicId())
                .projectId(entity.getProjectId())
                .channelId(entity.getChannelId())
                .categoryMajor(entity.getCategoryMajor())
                .categoryMinor(entity.getCategoryMinor())
                .introText(entity.getIntroText())
                .thumbnailUrl(entity.getThumbnailUrl())
                .status(entity.getStatus())
                .scheduledStartAt(entity.getScheduledStartAt())
                .actualStartAt(entity.getActualStartAt())
                .actualEndAt(entity.getActualEndAt())
                .vodUrl(entity.getVodUrl())
                .vodReadyAt(entity.getVodReadyAt())
                .likeCount(entity.getLikeCount())
                .ivsChatRoomArn(entity.getIvsChatRoomArn())
                .errorDetail(entity.getErrorDetail())
                .errorOccurredAt(entity.getErrorOccurredAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * <b>신규 생성 전용이다.</b> 갱신에 쓰면 전 컬럼 merge가 되어 도메인이 쓰지 않는
     * 값(likeCount·vodUrl)까지 되돌린다 — 갱신은 어댑터의 {@code applyFrom}이 한다.
     */
    static LiveSessionJpaEntity toEntity(LiveSession session) {
        return LiveSessionJpaEntity.builder()
                .id(session.getId())
                .publicId(session.getPublicId())
                .projectId(session.getProjectId())
                .channelId(session.getChannelId())
                .categoryMajor(session.getCategoryMajor())
                .categoryMinor(session.getCategoryMinor())
                .introText(session.getIntroText())
                .thumbnailUrl(session.getThumbnailUrl())
                .status(session.getStatus())
                .scheduledStartAt(session.getScheduledStartAt())
                .actualStartAt(session.getActualStartAt())
                .actualEndAt(session.getActualEndAt())
                .vodUrl(session.getVodUrl())
                .vodReadyAt(session.getVodReadyAt())
                .likeCount(session.getLikeCount())
                .ivsChatRoomArn(session.getIvsChatRoomArn())
                .errorDetail(session.getErrorDetail())
                .errorOccurredAt(session.getErrorOccurredAt())
                .build();
    }
}

package com.fundit.live.domain.session;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 복잡한 애그리거트(persistence-convention.md 0번) — 상태 전이 규칙이 있다.
 * 나머지 7개 테이블은 값 저장·조회가 전부라 단순 애그리거트로 둔다(JpaEntity + JpaRepository만).
 *
 * <p>소유권(sellerId)은 이 애그리거트가 아니라 {@code live_channels}가 갖는다.
 * 조회 시점에 채널을 조인해 본인 것만 가져오므로 여기에 sellerId를 복사해 두지 않는다 —
 * 복사하면 두 곳이 어긋날 수 있고, 어긋나는 쪽이 인가 판정이다.
 */
@Getter
@Builder(toBuilder = true)
public class LiveSession {

    private final Long id;
    /** 외부 노출용 liveId. 내부 PK(BIGINT)를 URL에 흘리면 전체 방송 수가 추측된다. */
    private final UUID publicId;
    /** project-service의 projects.public_id. 변환 없이 그대로 저장한다(V1 주석 참고). */
    private final UUID projectId;
    private final Long channelId;

    private String categoryMajor;
    private String categoryMinor;
    /** LIVE 소개 문구(요구사항정의서 6.2.4.1). 목록 카드에 노출되는 문구도 이 값이다. */
    private String introText;
    private String thumbnailUrl;

    private LiveStatus status;
    private Instant scheduledStartAt;
    private Instant actualStartAt;
    private Instant actualEndAt;

    private String errorDetail;
    private Instant errorOccurredAt;

    private final String vodUrl;
    private final Instant vodReadyAt;
    private final int likeCount;
    private String ivsChatRoomArn;
    private final Instant createdAt;

    public static LiveSession create(Long channelId, UUID projectId) {
        return LiveSession.builder()
                .publicId(UUID.randomUUID())
                .channelId(channelId)
                .projectId(projectId)
                .status(LiveStatus.DRAFT)
                .build();
    }

    /**
     * 기본 설정 부분 업데이트(요구사항정의서 6.2.4.1). null인 필드는 건드리지 않는다 —
     * 임시저장을 이어서 작성하는 화면이라 매번 전체를 보내지 않는다.
     *
     * <p>{@code scheduledStartAt}이 채워지면 DRAFT → SCHEDULED로 올라간다. 생성 시점에
     * SCHEDULED로 두지 않는 이유가 이것이다 — 예정 시각 없는 예약 상태를 만들지 않는다.
     *
     * <p>연결 프로젝트는 이 메서드로 바꿀 수 없다(요구사항정의서 6.2.4.1 "변경 불가").
     */
    public void updateSettings(String categoryMajor, String categoryMinor, String introText,
                               String thumbnailUrl, Instant scheduledStartAt) {
        requireStartable();
        if (categoryMajor != null) this.categoryMajor = categoryMajor;
        if (categoryMinor != null) this.categoryMinor = categoryMinor;
        if (introText != null) this.introText = introText;
        if (thumbnailUrl != null) this.thumbnailUrl = thumbnailUrl;
        if (scheduledStartAt != null) {
            this.scheduledStartAt = scheduledStartAt;
            if (this.status == LiveStatus.DRAFT) {
                this.status = LiveStatus.SCHEDULED;
            }
        }
    }

    /**
     * 송출 시작. DRAFT(즉시 시작)와 SCHEDULED(예약분) 둘 다 허용한다.
     *
     * <p>채팅방 ARN을 여기서 받는 이유: 채팅 적재가 룸 ARN으로 세션을 찾으므로
     * 저장하지 않으면 들어온 메시지를 어느 방송에 붙일지 알 수 없다.
     */
    public void start(Instant now, String chatRoomArn) {
        requireStartable();
        this.ivsChatRoomArn = chatRoomArn;
        this.status = LiveStatus.LIVE;
        this.actualStartAt = now;
        this.errorDetail = null;
        this.errorOccurredAt = null;
    }

    /** 송출 종료. LIVE 상태에서만 가능하다. */
    public void end(Instant now) {
        if (this.status != LiveStatus.LIVE) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "진행 중인 방송이 아닙니다.");
        }
        this.status = LiveStatus.ENDED;
        this.actualEndAt = now;
    }

    /**
     * 송출 실패. 상태를 ERROR로 내리고 사유를 남긴다 — 그냥 예외만 던지면 판매자 화면이
     * "무슨 일이 있었는지"를 보여줄 수 없다(요구사항정의서 6.3.4).
     */
    public void markError(String detail, Instant now) {
        // 끝난 방송을 오류로 뒤집지 않는다. 지금은 start()가 requireStartable()로 앞에서 걸러
        // 닿지 않지만, 가드를 호출부에만 두면 호출부가 늘 때 빠진다(end()와 같은 규칙이다).
        if (this.status == LiveStatus.ENDED) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "이미 종료된 방송입니다.");
        }
        this.status = LiveStatus.ERROR;
        this.errorDetail = detail;
        this.errorOccurredAt = now;
    }

    /** 소비자 목록·배너에 노출해도 되는 상태인지. DRAFT는 설정이 끝나지 않은 방송이라 제외한다. */
    public boolean isPubliclyVisible() {
        return this.status != LiveStatus.DRAFT;
    }

    /**
     * 아직 방송 전이라 설정·시작이 가능한 상태인지. 아니면 409를 던진다.
     *
     * <p><b>외부 자원을 만들기 전에 먼저 부르라고 public으로 열어뒀다.</b> IVS 채팅방을 만든 뒤에
     * 검증하면 이미 끝난 방송에 시작 요청이 들어왔을 때 두 갈래로 다 깨진다 —
     * IVS가 실패하면 ENDED가 ERROR로 덮여 다시보기 조회가 막히고,
     * 성공하면 채팅방만 만들어진 채 409가 나 자원이 샌다.
     *
     * <p>ERROR를 허용하는 이유: 송출 실패는 재시도할 수 있어야 한다(요구사항정의서 6.3.4의
     * "오류상태를 안내"는 끝이 아니라 다시 시도하라는 뜻이다). 막아두면 실패한 방송은
     * 영영 못 열고 판매자가 LIVE를 새로 만들어야 한다.
     */
    public void requireStartable() {
        if (this.status == LiveStatus.LIVE || this.status == LiveStatus.ENDED) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "이미 시작되었거나 종료된 방송입니다.");
        }
    }
}

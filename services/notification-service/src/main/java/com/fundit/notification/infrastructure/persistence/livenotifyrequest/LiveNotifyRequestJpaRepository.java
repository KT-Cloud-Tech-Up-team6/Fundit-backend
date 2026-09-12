package com.fundit.notification.infrastructure.persistence.livenotifyrequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface LiveNotifyRequestJpaRepository
        extends JpaRepository<LiveNotifyRequestJpaEntity, LiveNotifyRequestId> {

    /**
     * 신청은 idempotent해야 한다 — 중복 요청·네트워크 재시도를 실패로 처리하지 않는다(찜 등록과 동일 원칙).
     * @Modifying 쿼리는 호출부가 트랜잭션 안에 있어야 동작한다(LiveNotifyService의 @Transactional에 의존).
     */
    @Modifying
    @Query(value = "INSERT INTO live_notify_requests (live_id, member_id) VALUES (:liveId, :memberId) "
            + "ON CONFLICT (live_id, member_id) DO NOTHING", nativeQuery = true)
    void insertIgnoringConflict(@Param("liveId") UUID liveId, @Param("memberId") UUID memberId);

    /** 해제도 idempotent — 이미 없는 신청의 해제도 정상(영향 행 0)으로 취급한다. */
    @Modifying
    @Query(value = "DELETE FROM live_notify_requests WHERE live_id = :liveId AND member_id = :memberId",
            nativeQuery = true)
    void deleteByLiveIdAndMemberId(@Param("liveId") UUID liveId, @Param("memberId") UUID memberId);
}

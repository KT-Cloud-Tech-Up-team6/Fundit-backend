package com.fundit.notification.infrastructure.persistence.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, Long> {

    /** NOTI-003. idx_notifications_member_created (member_id, created_at DESC)가 정렬까지 커버한다. */
    Page<NotificationJpaEntity> findByMemberIdOrderByCreatedAtDesc(UUID memberId, Pageable pageable);

    /** NOTI-007. idx_notifications_unread (member_id) WHERE read_at IS NULL partial 인덱스로 처리된다. */
    long countByMemberIdAndReadAtIsNull(UUID memberId);

    /**
     * NOTI-005. 소유자 조건을 쿼리에 묶는 이유(security.md S10): findById 후 소유자를 비교하면
     * "없음"과 "있는데 남의 것"이 코드상 갈라져 403이 새어나갈 여지가 생긴다. 403은 "그 알림이 존재한다"를
     * 알려주므로 ID를 넣어보며 타인 알림의 존재 여부를 캐낼 수 있다. 한 쿼리로 묶으면 두 경우가
     * 같은 Optional.empty()가 되어 404가 자연히 나온다.
     */
    Optional<NotificationJpaEntity> findByIdAndMemberId(Long id, UUID memberId);

    /**
     * NOTI-006 적재. Kafka는 at-least-once고 발행 측 아웃박스도 "전송 성공 후 published_at 기록 전
     * 장애"면 재발행하므로 중복은 예정된 일이다. 컨슈머에 중복 판정 로직을 짜는 대신
     * uq_notifications_event_member (event_id, member_id) 대상 ON CONFLICT DO NOTHING으로 끝낸다.
     * (member_id를 함께 묶는 이유: PROJECT_OPEN처럼 이벤트 하나가 수신자 여러 명으로 팬아웃되면
     * event_id 단독 UNIQUE는 두 번째 수신자부터 제약 위반이 난다.)
     *
     * <p>@Modifying 쿼리는 호출부가 트랜잭션 안에 있어야 동작한다(NotificationAppendService의 @Transactional에 의존).
     */
    @Modifying
    @Query(value = "INSERT INTO notifications (event_id, member_id, notif_type, title, related_url, created_at) "
            + "VALUES (:eventId, :memberId, :notifType, :title, :relatedUrl, now()) "
            + "ON CONFLICT (event_id, member_id) DO NOTHING", nativeQuery = true)
    void insertIgnoringConflict(@Param("eventId") String eventId,
                                @Param("memberId") UUID memberId,
                                @Param("notifType") String notifType,
                                @Param("title") String title,
                                @Param("relatedUrl") String relatedUrl);
}

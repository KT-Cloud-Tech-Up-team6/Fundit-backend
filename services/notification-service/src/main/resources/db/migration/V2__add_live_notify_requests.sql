-- LIVE 시작 알림 신청 목록 (NOTI-002).
-- ERD는 이 테이블을 live-service DB에 두고 있으나 live-service는 개발 착수 전이고,
-- 실제로 알림을 발송하는 주체가 notification-service라 이쪽이 소유한다
-- (NotificationDomainApiSpec.md NOTI-002 "미결 — 신청 데이터 소유 서비스" 참고).
-- live-service 착수 시점에 ERD 담당자와 재확인 필요.
CREATE TABLE live_notify_requests (
                                      live_id   UUID NOT NULL,   -- live-service 참조, FK 아님. 타입은 ERD 미확인 상태의 가정
                                      member_id UUID NOT NULL,   -- member-service 참조, FK 아님
    -- live_id가 선두인 이유: 화면 진입 시의 "내가 신청했나"는 1건 조회지만,
    -- LIVE 시작 시의 "신청자 전원"은 live_id 하나로 N건을 훑는다.
    -- member_id가 선두면 그 발송 쿼리가 전체 스캔이 된다.
                                      PRIMARY KEY (live_id, member_id)
);

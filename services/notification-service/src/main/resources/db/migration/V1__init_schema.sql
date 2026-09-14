-- ============================================================
-- 8. notification-service
-- ============================================================
-- 초안 대비 변경 내역은 이 파일 하단 주석 참고 (2026-09-11).
-- 관련 문서: NotificationFunctionalSpec.md / NotificationDomainApiSpec.md

-- 온사이트 알림함. MVP 채널은 온사이트 하나뿐이라 채널 축을 두지 않는다.
-- 알림 생성은 전부 Kafka 컨슈머를 거치며, 수신자(member_id)는 이벤트에 이미 실려 온다
-- (notification-service가 order/payment/member에 수신자를 되묻지 않는다).
CREATE TABLE notifications (
                               id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                               event_id    VARCHAR(64)  NOT NULL,       -- 발행 측이 싣는 이벤트 고유 ID(중복 소비 차단용)
                               member_id   UUID         NOT NULL,       -- member-service 참조, FK 아님
                               notif_type  VARCHAR(30)  NOT NULL,       -- LIVE_START/COMMUNITY_ANSWER/SHIPPING_UPDATE/REFUND_STATUS/
    -- PROJECT_OPEN/REWARD_RESTOCK/COUPON_EXPIRING/SELLER_UPDATE_DUE
                               title       VARCHAR(100) NOT NULL,
                               related_url TEXT         NOT NULL,       -- 알림 선택 시 이동 경로. 현재 8종 전부 목적지가 있다
                               read_at     TIMESTAMPTZ,
                               created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 유일한 읽기 경로가 NOTI-003 "본인 알림 최신순 페이징"이라 정렬 키를 인덱스에 넣는다.
CREATE INDEX idx_notifications_member_created ON notifications (member_id, created_at DESC);

-- NOTI-007 안읽음 개수 뱃지용. 안읽은 행만 인덱싱해서 알림이 쌓여도 인덱스가 커지지 않는다.
CREATE INDEX idx_notifications_unread ON notifications (member_id) WHERE read_at IS NULL;

-- Kafka는 at-least-once다. 같은 이벤트를 두 번 소비해도 알림이 두 개 생기지 않게 막는다.
-- member_id를 함께 묶는 이유: PROJECT_OPEN처럼 이벤트 하나가 수신자 여러 명으로 팬아웃되면
-- event_id 단독 UNIQUE는 두 번째 수신자부터 제약 위반이 난다.
CREATE UNIQUE INDEX uq_notifications_event_member ON notifications (event_id, member_id);

-- [천장] 온사이트 전용인 동안만 이 구조로 충분하다.
-- 지금은 이벤트 하나의 사이드이펙트가 "notifications INSERT" 하나뿐이라, 중복 소비는 위 UNIQUE로 끝난다.
-- 이메일·웹푸시가 붙으면 사이드이펙트가 둘이 되어 "INSERT 성공 → 발송 전 장애 → 재수신 시
-- UNIQUE로 스킵 → 발송 영영 유실"이 생긴다. 그때는 발송 상태 컬럼(sent_at 등) + 재시도 워커가 필요하다.
-- 협의처: 채널 확장 범위는 PM, 재시도 구조는 내부 설계.

-- 수신 거부 목록. "행이 존재하면 그 유형을 받지 않는다".
-- 기본값이 전체 수신이라 신규 회원 초기 데이터가 필요 없고, 설정 변경이 insert/delete로 끝난다.
CREATE TABLE notification_settings (
                                       member_id  UUID        NOT NULL,         -- member-service 참조, FK 아님
                                       notif_type VARCHAR(30) NOT NULL,
                                       PRIMARY KEY (member_id, notif_type)
);
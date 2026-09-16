-- notification.raised.v1 발행을 실제로 배선하면서, 알림 아웃박스 행이 "수신자 1명"을
-- 나타내도록 member_id를 추가한다. ScheduleChanged(참여자 전원 팬아웃)는 참가자 수만큼
-- 행 자체를 여러 개 적재하는 방식을 쓴다(행 1개 = 수신자 1명).
-- 기존 테이블에 이미 쌓여 있을 수 있는 미발행 행과의 호환을 위해 컬럼 자체는 nullable로 두되,
-- 애플리케이션 레벨(OutboxFulfillmentNotificationPublisher)에서는 항상 채워서 적재한다.
ALTER TABLE fulfillment_event_outbox ADD COLUMN member_id UUID;

-- 알림 relatedUrl은 사용자에게 보여줄 딥링크라, 내부 PK(project_id/funding_id)를 그대로
-- 노출하지 않고 외부 노출용 publicId를 따로 저장한다. 이벤트 타입에 따라 프로젝트 publicId
-- (StaleUpdateReminder) 또는 펀딩 publicId(ReceiptAutoConfirmed)가 들어간다 — 도메인이 다른
-- 값을 한 컬럼에 담으므로 이름을 특정 애그리거트에 묶지 않는다.
ALTER TABLE fulfillment_event_outbox ADD COLUMN related_public_id UUID;

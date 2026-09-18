-- 리워드 상세페이지에 배송비/예상 발송일 표시 필요(FE 요청, ProjectDomainPendingWork.md #1).
-- 기획 확정 전까지 유연한 기본 스키마로 우선 구현한다:
--   shipping_fee: 리워드별 nullable 배송비(NULL=미설정, 0=무료배송)
--   estimated_delivery_days: "펀딩 종료 후 N일" 상대값(고정 일자보다 일정 밀림에 안전).
-- 기획 답이 다르게 나오면(프로젝트 공통 배송비, 고정 일자 등) 후속 마이그레이션으로 조정한다.
ALTER TABLE rewards
    ADD COLUMN shipping_fee             BIGINT,
    ADD COLUMN estimated_delivery_days  INTEGER;

ALTER TABLE rewards
    ADD CONSTRAINT chk_rewards_shipping_fee CHECK (shipping_fee IS NULL OR shipping_fee >= 0),
    ADD CONSTRAINT chk_rewards_estimated_delivery_days CHECK (estimated_delivery_days IS NULL OR estimated_delivery_days >= 0);

-- cross-service ID(Long↔UUID) 통일(#69) — projectId는 project-service publicId,
-- fundingId는 order-service orderId(Funding.publicId). 레거시 Long 컬럼은 과거 행
-- 조회용으로만 남기고, 신규 행에는 UUID만 채운다(애플리케이션은 Long을 쓰지 않음).

ALTER TABLE fulfillment_trackers ADD COLUMN project_public_id UUID;
CREATE UNIQUE INDEX uq_fulfillment_trackers_project_public ON fulfillment_trackers (project_public_id);

ALTER TABLE shipments ADD COLUMN project_public_id UUID;
ALTER TABLE shipments ADD COLUMN funding_order_id UUID;
CREATE UNIQUE INDEX uq_shipments_funding_order ON shipments (funding_order_id);

-- 신규 행은 Long project_id가 비어 있어 기존 FK를 만족할 수 없다.
ALTER TABLE shipments DROP CONSTRAINT fk_shipments_tracker_project;

ALTER TABLE fulfillment_trackers ALTER COLUMN project_id DROP NOT NULL;
ALTER TABLE shipments ALTER COLUMN project_id DROP NOT NULL;
ALTER TABLE shipments ALTER COLUMN funding_id DROP NOT NULL;

ALTER TABLE shipments
    ADD CONSTRAINT fk_shipments_tracker_project_public
    FOREIGN KEY (project_public_id) REFERENCES fulfillment_trackers (project_public_id);

-- 알림/도메인 아웃박스: 애플리케이션이 UUID를 쓰므로 별도 컬럼을 둔다.
-- 레거시 Long은 Kafka 파티션 키 호환을 위해 nullable로 남긴다.
ALTER TABLE fulfillment_event_outbox ADD COLUMN project_public_id UUID;
ALTER TABLE fulfillment_event_outbox ADD COLUMN funding_order_id UUID;

ALTER TABLE fulfillment_domain_event_outbox ADD COLUMN project_public_id UUID;
ALTER TABLE fulfillment_domain_event_outbox ADD COLUMN funding_order_id UUID;
ALTER TABLE fulfillment_domain_event_outbox ALTER COLUMN project_id DROP NOT NULL;
ALTER TABLE fulfillment_domain_event_outbox ALTER COLUMN funding_id DROP NOT NULL;

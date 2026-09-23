-- FULFILLMENT-002 — 세부 진행 기록에 판매자가 등록한 사진 URL을 함께 저장한다.
ALTER TABLE fulfillment_stage_details
    ADD COLUMN photo_urls JSONB;

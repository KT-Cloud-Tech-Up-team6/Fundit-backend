-- PROJECT-008/PROJECT-027(리워드 법정고시) MVP 범위 제외(PM 확정) — 관련 컬럼 제거.
-- 품목마다 필요한 고시 항목이 달라 MVP 단계에서 표준화하기 어렵다는 사유.
ALTER TABLE rewards
    DROP COLUMN category_type,
    DROP COLUMN disclosure;

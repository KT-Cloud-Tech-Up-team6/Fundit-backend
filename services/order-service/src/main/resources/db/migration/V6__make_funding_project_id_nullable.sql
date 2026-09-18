-- cross-service ID(Long↔UUID) 통일 2단계 — 애플리케이션은 이제 project_public_id(UUID)만
-- 채운다. 레거시 project_id(Long)는 과거 데이터 조회·백필용으로만 남겨두고, 새로 생성되는
-- 펀딩에는 더 이상 값을 쓰지 않으므로 NOT NULL 제약을 해제한다.
ALTER TABLE fundings ALTER COLUMN project_id DROP NOT NULL;

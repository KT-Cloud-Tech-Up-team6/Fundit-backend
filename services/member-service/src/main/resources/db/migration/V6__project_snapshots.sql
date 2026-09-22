-- 찜 목록 표시용 프로젝트 스냅샷. project-service의 project.approved.v1 / project.updated.v1을 구독해 채운다.
-- wishes 행에 직접 쓰지 않는 이유: 승인 이벤트가 찜보다 먼저 와서, 나중에 생긴 찜 행은 스냅샷을 받을 기회가 없다.
CREATE TABLE project_snapshots (
    project_id        BIGINT PRIMARY KEY,
    project_public_id UUID NOT NULL,
    title             VARCHAR(200),
    thumbnail_url     VARCHAR(500),
    -- 발행 측 아웃박스 id(전역 단조 증가). 옛 이벤트가 최신 값을 덮지 않게 비교한다.
    source_version    BIGINT,
    synced_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

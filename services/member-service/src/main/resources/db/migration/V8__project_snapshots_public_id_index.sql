-- 프로젝트 상세(UUID) 기준 찜 API가 공개 id로 숫자 id를 찾는다(#192). 요청마다 조회하므로 인덱스를 두고,
-- 공개 id는 프로젝트당 하나라 유니크로 건다(project_id가 PK라 기존 행끼리 겹칠 수 없다).
CREATE UNIQUE INDEX uq_project_snapshots_public_id ON project_snapshots (project_public_id);

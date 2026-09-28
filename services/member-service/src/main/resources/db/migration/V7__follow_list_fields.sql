-- 팔로잉 목록 표시 필드(#168): 판매자 프로필 이미지, 팔로워 수, ♥(판매자 프로젝트 찜 합산).

-- 프로필 이미지. 업로드·수정 경로가 아직 없어 값은 항상 NULL이다(FE가 기본 이미지로 대체).
ALTER TABLE members ADD COLUMN profile_image_url VARCHAR(500);

-- ♥ 합산용. project.approved.v1/project.updated.v1의 sellerId를 저장한다.
-- 이 컬럼 추가 전에 받은 스냅샷은 NULL이라, project 이벤트가 다시 올 때까지 합산에서 빠진다.
ALTER TABLE project_snapshots ADD COLUMN seller_id UUID;

-- 목록 쿼리의 판매자별 서브쿼리용. follows PK와 uq_wishes_member_project는 member_id가 선두라 쓰이지 않는다.
CREATE INDEX idx_follows_seller ON follows (seller_id);
CREATE INDEX idx_project_snapshots_seller ON project_snapshots (seller_id);
CREATE INDEX idx_wishes_project ON wishes (project_id);

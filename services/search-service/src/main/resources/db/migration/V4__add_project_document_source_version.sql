-- SEARCH-011. project.approved.v1/project.updated.v1이 서로 다른 토픽이라 순서 역전이
-- 가능하다. 발행 측 아웃박스 id(전역 단조 증가)를 실어, 더 낮은 버전의 이벤트가
-- 이미 색인된 제목·카테고리·판매자 정보를 덮어쓰지 않게 한다.
ALTER TABLE project_documents
    ADD COLUMN source_version BIGINT;

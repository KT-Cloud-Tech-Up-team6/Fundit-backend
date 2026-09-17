-- 찜 집계 읽기모델의 멱등 구독용. 같은 (project_id, member_id) 이벤트가 다시 와도
-- project_documents.wish_count를 중복 가감하지 않는다(project.wished.v1/unwished.v1) —
-- project-service project_wish_stat_members와 동일 패턴(SearchDomainFunctionalSpec.md SEARCH-014).
CREATE TABLE search_wish_stat_members (
    project_id BIGINT NOT NULL,
    member_id  UUID NOT NULL,
    PRIMARY KEY (project_id, member_id)
);

-- 찜 집계 읽기모델의 멱등 구독용. 같은 (project_id, member_id) 이벤트가 다시 와도
-- wish_count를 중복 가감하지 않는다(ProjectWished/ProjectUnwished).
CREATE TABLE project_wish_stat_members (
    project_id BIGINT NOT NULL,
    member_id  UUID NOT NULL,
    PRIMARY KEY (project_id, member_id)
);

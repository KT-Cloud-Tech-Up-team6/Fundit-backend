
-- ============================================================
-- 3. project-service (프로젝트/리워드/커뮤니티)
-- [PM 전달 문서 반영] business_type 추가, Reward 구조 개편
--   (기존 평면형 reward_options → RewardOptionGroup/RewardOptionValue 2단 구조)
-- ============================================================

-- updated_at 자동 갱신 트리거 함수 (이 데이터베이스 내 모든 테이블이 공유)
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE categories (
                            category_major VARCHAR(50) NOT NULL,
                            category_minor VARCHAR(50) NOT NULL,
                            display_order  INT NOT NULL DEFAULT 0,
                            PRIMARY KEY (category_major, category_minor)
);

CREATE TABLE projects (
                          id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                          public_id        UUID NOT NULL,                -- 애플리케이션에서 생성한 UUID v7, 외부 노출용(URL 등)
                          seller_id        UUID NOT NULL,                -- member-service 참조, FK 아님
                          business_type    VARCHAR(10) NOT NULL
                              CHECK (business_type IN ('GENERAL','SOLE','CORP')), -- 일반/개인사업자/법인사업자
                          category_major   VARCHAR(50) NOT NULL,
                          category_minor   VARCHAR(50) NOT NULL,
                          title            VARCHAR(40) NOT NULL,
                          goal_amount      BIGINT NOT NULL CHECK (goal_amount >= 500000),
                          funding_start_at TIMESTAMP,               -- 검수 승인 시점에 확정(아래 project_review_requests 참고)
                          funding_deadline TIMESTAMP,                -- [수정] 생성 시점엔 미확정일 수 있어 NOT NULL 제거, 검수 승인 시 확정
                          status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                              CHECK (status IN ('DRAFT','PENDING_REVIEW','ONGOING','SUCCEEDED','FAILED')),
                          created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          deleted_at       TIMESTAMP,
                          project_display_code VARCHAR(20) GENERATED ALWAYS AS ('F' || LPAD(id::text, 7, '0')) STORED,
                          CONSTRAINT fk_projects_category FOREIGN KEY (category_major, category_minor)
                              REFERENCES categories (category_major, category_minor)
);
CREATE UNIQUE INDEX uq_projects_public_id ON projects (public_id);
CREATE UNIQUE INDEX uq_projects_display_code ON projects (project_display_code);
CREATE INDEX idx_projects_seller ON projects (seller_id);
CREATE INDEX idx_projects_status ON projects (status);
CREATE INDEX idx_projects_category ON projects (category_major, category_minor);
CREATE TRIGGER trg_projects_updated_at
    BEFORE UPDATE ON projects
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE project_review_requests (
                                         id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                         project_id        BIGINT NOT NULL,
                                         status            VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED'
                                             CHECK (status IN ('SUBMITTED','APPROVED','REJECTED')),
                                         reject_reason     TEXT,
                                         reviewer_id       UUID,                  -- accounts.id 참조(role=ADMIN), FK 아님
                                         submitted_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         reviewed_at       TIMESTAMPTZ,
                                         CONSTRAINT fk_project_review_requests_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
CREATE INDEX idx_project_review_requests_project ON project_review_requests (project_id, submitted_at DESC);

CREATE TABLE project_open_notify_requests (
                                              id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                              project_id BIGINT NOT NULL,
                                              member_id  UUID NOT NULL,               -- member-service 참조, FK 아님
                                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              CONSTRAINT fk_project_open_notify_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
CREATE UNIQUE INDEX uq_project_open_notify ON project_open_notify_requests (project_id, member_id);

CREATE TABLE rewards (
                         id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                         project_id             BIGINT NOT NULL,
                         name                   VARCHAR(100) NOT NULL,
                         description            TEXT NOT NULL,
                         image_url              TEXT,                              -- [신규] 리워드 이미지 경로, 선택
                         price                  BIGINT NOT NULL CHECK (price >= 0),
                         is_limited             BOOLEAN NOT NULL DEFAULT FALSE,     -- [수정] is_unlimited 대체(의미 반전)
                         quantity               INT,                                -- [신규] null = 제한 없음
                         is_early_bird          BOOLEAN NOT NULL DEFAULT FALSE,     -- PM 문서의 is_earlybird와 동일 의미
                         has_option             BOOLEAN NOT NULL DEFAULT FALSE,     -- [신규] true면 reward_option_groups 존재
                         sort_order             INT NOT NULL DEFAULT 0,             -- [수정] display_order → sort_order
                         category_type          VARCHAR(30),
                         disclosure             JSONB,
                         simple_refund_disabled BOOLEAN NOT NULL DEFAULT FALSE,
                         created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         deleted_at             TIMESTAMP,   -- 소프트 삭제. project-api-spec의 "삭제된 리워드는 응답에서 제외" 규칙 지원
    -- [신규 추가] PM 전달 문서: "리워드 등록 순으로 R0000001부터 오름차순 부여(시스템 자동 생성)"
                         reward_display_code    VARCHAR(20) GENERATED ALWAYS AS ('R' || LPAD(id::text, 7, '0')) STORED,
                         CONSTRAINT fk_rewards_project FOREIGN KEY (project_id) REFERENCES projects(id),
    -- [신규] is_limited=true면 quantity 필수, false면 quantity는 반드시 NULL(무제한)
                         CONSTRAINT chk_rewards_quantity CHECK (
                             (is_limited = TRUE  AND quantity IS NOT NULL AND quantity >= 0) OR
                             (is_limited = FALSE AND quantity IS NULL)
                             )
);
CREATE UNIQUE INDEX uq_rewards_display_code ON rewards (reward_display_code);
CREATE INDEX idx_rewards_project ON rewards (project_id);
CREATE TRIGGER trg_rewards_updated_at
    BEFORE UPDATE ON rewards
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- [신규 추가] RewardOptionGroup — 옵션 설정 체크(has_option=true) 시에만 생성 (예: 색상, 사이즈)
CREATE TABLE reward_option_groups (
                                      id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                      reward_id  BIGINT NOT NULL,
                                      name       VARCHAR(50) NOT NULL,            -- 예: 색상, 사이즈
                                      sort_order INT NOT NULL DEFAULT 0,
                                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      deleted_at TIMESTAMP,
                                      CONSTRAINT fk_reward_option_groups_reward FOREIGN KEY (reward_id) REFERENCES rewards(id)
);
CREATE INDEX idx_reward_option_groups_reward ON reward_option_groups (reward_id);
CREATE TRIGGER trg_reward_option_groups_updated_at
    BEFORE UPDATE ON reward_option_groups
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- [신규 추가] RewardOptionValue — 분류 하위의 개별 옵션 값 (예: 화이트, 블랙)
CREATE TABLE reward_option_values (
                                      id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                      option_group_id BIGINT NOT NULL,
                                      value           VARCHAR(50) NOT NULL,        -- 예: 화이트, 블랙
                                      sort_order      INT NOT NULL DEFAULT 0,
                                      created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      deleted_at      TIMESTAMP,
                                      CONSTRAINT fk_reward_option_values_group FOREIGN KEY (option_group_id) REFERENCES reward_option_groups(id)
);
CREATE INDEX idx_reward_option_values_group ON reward_option_values (option_group_id);
CREATE TRIGGER trg_reward_option_values_updated_at
    BEFORE UPDATE ON reward_option_values
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE community_posts (
                                 id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 project_id BIGINT NOT NULL,
                                 member_id  UUID NOT NULL,               -- member-service 참조, FK 아님
                                 post_type  VARCHAR(10) NOT NULL CHECK (post_type IN ('QUESTION','CHEER')),
                                 content    TEXT NOT NULL,
                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 CONSTRAINT fk_community_posts_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
CREATE INDEX idx_community_posts_project ON community_posts (project_id);

CREATE TABLE community_answers (
                                   id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                   post_id    BIGINT NOT NULL,
                                   seller_id  UUID NOT NULL,               -- member-service 참조, FK 아님(판매자=회원)
                                   content    TEXT NOT NULL,
                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   CONSTRAINT fk_community_answers_post FOREIGN KEY (post_id) REFERENCES community_posts(id)
);
CREATE UNIQUE INDEX uq_community_answers_post ON community_answers (post_id); -- 질문당 답변 1개 가정
CREATE TRIGGER trg_community_answers_updated_at
    BEFORE UPDATE ON community_answers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE project_notices (
                                 id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 project_id  BIGINT NOT NULL,
                                 notice_type VARCHAR(20) NOT NULL,
                                 title       VARCHAR(100) NOT NULL,
                                 content     TEXT NOT NULL,
                                 created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 CONSTRAINT fk_project_notices_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
CREATE INDEX idx_project_notices_project ON project_notices (project_id, notice_type);

-- [신규 추가] PRD 12.3.4 "게시물 댓글 기능 및 사용자 팔로우 기능" 반영.
-- 기존 ERD에는 테이블이 없어 구현이 불가능했음. 대댓글은 범위에 없다고 보고 1단계 댓글로 설계.
CREATE TABLE project_notice_comments (
                                         id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                         notice_id  BIGINT NOT NULL,
                                         member_id  UUID NOT NULL,               -- member-service 참조, FK 아님
                                         content    VARCHAR(500) NOT NULL,
                                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         deleted_at TIMESTAMP,
                                         CONSTRAINT fk_project_notice_comments_notice FOREIGN KEY (notice_id) REFERENCES project_notices(id)
);
CREATE INDEX idx_project_notice_comments_notice ON project_notice_comments (notice_id, created_at);

-- "사용자 팔로우"는 PRD 문맥상 새소식 게시물이 아니라 판매자(메이커)를 구독하는 개념에 가까움
-- (streaming.seller_follows와 동일 개념). 새 테이블을 또 만들지 않고 프로젝트 단위 팔로우로
-- 최소 범위만 둔다 — 판매자 팔로우와의 통합 여부는 재검토 필요(ERD 하단 이슈 2번과 동일 성격).
CREATE TABLE project_follows (
                                 id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 project_id BIGINT NOT NULL,
                                 member_id  UUID NOT NULL,               -- member-service 참조, FK 아님
                                 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 CONSTRAINT fk_project_follows_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
CREATE UNIQUE INDEX uq_project_follows ON project_follows (project_id, member_id);

CREATE TABLE reviews (
                         id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                         project_id BIGINT NOT NULL,
                         funding_id BIGINT NOT NULL,     -- order-service 참조, FK 아님
                         member_id  UUID NOT NULL,
                         content    TEXT NOT NULL,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         CONSTRAINT fk_reviews_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
CREATE INDEX idx_reviews_project ON reviews (project_id);
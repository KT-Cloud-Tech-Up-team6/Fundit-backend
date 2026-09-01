-- ============================================================
-- 3. project-service (프로젝트/리워드/커뮤니티)
-- ============================================================

-- updated_at 자동 갱신 트리거 함수 (이 데이터베이스 내 모든 테이블이 공유)
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- [수정: 카테고리 자유입력 → 마스터 테이블화]
-- PRD 4.2.4에 정의된 대분류/상세분류 조합만 허용하기 위해 마스터 테이블을 두고,
-- projects는 (category_major, category_minor) 복합키를 FK로 참조한다.
-- 값 목록은 PRD 4.2.4 표를 그대로 시드 데이터로 적재한다.
-- TODO: 시드 데이터는 별도 마이그레이션(V2)으로 적재한다. PRD 4.2.4 표가 이 레포의
--       docs/PRD.md(요약본)에는 없어 원본 확인 후 추가할 것 — 적재 전까지 basic-info
--       저장은 FK 위반으로 실패한다.
CREATE TABLE categories
(
    category_major VARCHAR(50) NOT NULL,
    category_minor VARCHAR(50) NOT NULL,
    display_order  INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (category_major, category_minor)
);

-- [수정: NOT NULL 완화] 프로젝트 생성(POST /api/projects)은 입력값 없이 seller_id만으로
-- DRAFT 행을 만든다. title/goal_amount/category_*는 이후 basic-info·detail 단계에서
-- 채워지므로 생성 시점엔 비어 있어야 한다. 필수값 검증은 검수 요청(detail.isDraft=false)
-- 시점에 애플리케이션이 수행한다.
-- CHECK/FK는 그대로 두어도 된다 — NULL이면 CHECK는 UNKNOWN으로 통과하고,
-- FK도 MATCH SIMPLE 기본 동작상 컬럼이 NULL이면 검사하지 않는다.
CREATE TABLE projects
(
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id           UUID        NOT NULL,               -- 애플리케이션에서 생성한 UUID v7, 외부 노출용
    seller_id           UUID        NOT NULL,               -- member-service 참조, FK 아님
    category_major      VARCHAR(50),
    category_minor      VARCHAR(50),
    business_type       VARCHAR(20) CHECK (business_type IN ('GENERAL', 'SOLE_PROPRIETOR', 'CORPORATION')),
    privacy_agreed      BOOLEAN     NOT NULL DEFAULT FALSE, -- 프로젝트 개설용 개인정보 수집 동의, false면 basic-info 저장 거부
    title               VARCHAR(40),
    thumbnail_image_url TEXT,                               -- 사전 업로드된 대표 이미지 URL
    intro_content       JSONB,                              -- 소개 텍스트/이미지/영상URL 조합
    goal_amount         BIGINT CHECK (goal_amount >= 500000),
    funding_start_at    TIMESTAMPTZ,                        -- 검수 승인 시점에 확정(아래 project_review_requests 참고)
    funding_deadline    TIMESTAMPTZ,                        -- [수정] 생성 시점엔 미확정일 수 있어 NOT NULL 제거, 검수 승인 시 확정
    -- [수정] 검수 대기 상태(PENDING_REVIEW) 추가 — 기존 enum에는 DRAFT에서 바로 ONGOING으로
    -- 넘어갈 방법이 없어 "검수 요청" 이후 상태를 표현할 수 없었음(ProjectDomainApiSpec의
    -- detail.isDraft=false 처리와 매칭).
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'ONGOING', 'SUCCEEDED', 'FAILED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT fk_projects_category FOREIGN KEY (category_major, category_minor)
        REFERENCES categories (category_major, category_minor)
);
CREATE UNIQUE INDEX uq_projects_public_id ON projects (public_id);
CREATE INDEX idx_projects_seller ON projects (seller_id);
CREATE INDEX idx_projects_status ON projects (status);
CREATE INDEX idx_projects_category ON projects (category_major, category_minor);
CREATE TRIGGER trg_projects_updated_at
    BEFORE UPDATE
    ON projects
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- [신규 추가] 프로젝트 검수/승인 이력 — PRD에 검수 주체·승인 API가 정의돼 있지 않아
-- DRAFT→PENDING_REVIEW→ONGOING(또는 반려 시 DRAFT로 복귀) 전이를 추적할 곳이 없었음.
-- 승인 시 reviewer가 funding_start_at/funding_deadline을 최종 확정한다.
CREATE TABLE project_review_requests
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id    BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED'
        CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED')),
    reject_reason TEXT,
    reviewer_id   UUID,                                        -- accounts.id 참조(role=ADMIN), FK 아님
    submitted_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at   TIMESTAMPTZ,
    CONSTRAINT fk_project_review_requests_project FOREIGN KEY (project_id) REFERENCES projects (id)
);
CREATE INDEX idx_project_review_requests_project ON project_review_requests (project_id, submitted_at DESC);

CREATE TABLE project_open_notify_requests
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id BIGINT      NOT NULL,
    member_id  UUID        NOT NULL,                        -- member-service 참조, FK 아님
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_open_notify_project FOREIGN KEY (project_id) REFERENCES projects (id)
);
CREATE UNIQUE INDEX uq_project_open_notify ON project_open_notify_requests (project_id, member_id);

CREATE TABLE rewards
(
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id             BIGINT       NOT NULL,
    name                   VARCHAR(100) NOT NULL,
    description            TEXT,
    price                  BIGINT       NOT NULL CHECK (price >= 0),
    is_unlimited           BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order          INT          NOT NULL DEFAULT 0,
    category_type          VARCHAR(30),
    disclosure             JSONB,
    is_early_bird          BOOLEAN      NOT NULL DEFAULT FALSE,
    simple_refund_disabled BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at             TIMESTAMPTZ,                        -- [수정] 소프트 삭제 컬럼 추가. ProjectDomainApiSpec의
    -- "삭제된 리워드는 응답에서 제외" 규칙이 이 컬럼 없이는 구현 불가했음.
    CONSTRAINT fk_rewards_project FOREIGN KEY (project_id) REFERENCES projects (id)
);
CREATE INDEX idx_rewards_project ON rewards (project_id);
CREATE TRIGGER trg_rewards_updated_at
    BEFORE UPDATE
    ON rewards
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- [정책 확정] is_unlimited=FALSE인 리워드의 "제한 수량"은 옵션별 개별 수량으로 확정한다.
-- 근거: inventories.reward_option_id 단위로 이미 재고를 관리하고 있고, order-service가
-- 리워드 합산 재고를 별도로 추적하지 않음. 리워드에 옵션이 여러 개면 옵션마다 독립적으로
-- 품절 판정한다(리워드 전체 합산 재고 개념은 두지 않음).

CREATE TABLE reward_options
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reward_id   BIGINT       NOT NULL,
    option_name VARCHAR(100) NOT NULL,
    sku         VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMPTZ,                                   -- [수정] 소프트 삭제 컬럼 추가(사유는 rewards.deleted_at과 동일)
    CONSTRAINT fk_reward_options_reward FOREIGN KEY (reward_id) REFERENCES rewards (id)
);
CREATE UNIQUE INDEX uq_reward_options_sku ON reward_options (sku);
CREATE INDEX idx_reward_options_reward ON reward_options (reward_id);
CREATE TRIGGER trg_reward_options_updated_at
    BEFORE UPDATE
    ON reward_options
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TABLE community_posts
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id BIGINT      NOT NULL,
    member_id  UUID        NOT NULL,                        -- member-service 참조, FK 아님
    post_type  VARCHAR(10) NOT NULL CHECK (post_type IN ('QUESTION', 'CHEER')),
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_community_posts_project FOREIGN KEY (project_id) REFERENCES projects (id)
);
CREATE INDEX idx_community_posts_project ON community_posts (project_id);

CREATE TABLE community_answers
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    BIGINT      NOT NULL,
    seller_id  UUID        NOT NULL,                        -- member-service 참조, FK 아님(판매자=회원)
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_community_answers_post FOREIGN KEY (post_id) REFERENCES community_posts (id)
);
CREATE UNIQUE INDEX uq_community_answers_post ON community_answers (post_id); -- 질문당 답변 1개 가정
CREATE TRIGGER trg_community_answers_updated_at
    BEFORE UPDATE
    ON community_answers
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TABLE project_notices
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    notice_type VARCHAR(20)  NOT NULL,
    title       VARCHAR(100) NOT NULL,
    content     TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_notices_project FOREIGN KEY (project_id) REFERENCES projects (id)
);
CREATE INDEX idx_project_notices_project ON project_notices (project_id, notice_type);

-- [신규 추가] PRD 12.3.4 "게시물 댓글 기능 및 사용자 팔로우 기능" 반영.
-- 기존 ERD에는 테이블이 없어 구현이 불가능했음. 대댓글은 범위에 없다고 보고 1단계 댓글로 설계.
CREATE TABLE project_notice_comments
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    notice_id  BIGINT        NOT NULL,
    member_id  UUID          NOT NULL,                      -- member-service 참조, FK 아님
    content    VARCHAR(500)  NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT fk_project_notice_comments_notice FOREIGN KEY (notice_id) REFERENCES project_notices (id)
);
CREATE INDEX idx_project_notice_comments_notice ON project_notice_comments (notice_id, created_at);

-- "사용자 팔로우"는 PRD 문맥상 새소식 게시물이 아니라 판매자(메이커)를 구독하는 개념에 가까움
-- (streaming.seller_follows와 동일 개념). 새 테이블을 또 만들지 않고 프로젝트 단위 팔로우로
-- 최소 범위만 둔다 — 판매자 팔로우와의 통합 여부는 재검토 필요(ERD 하단 이슈 2번과 동일 성격).
CREATE TABLE project_follows
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id BIGINT      NOT NULL,
    member_id  UUID        NOT NULL,                        -- member-service 참조, FK 아님
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_follows_project FOREIGN KEY (project_id) REFERENCES projects (id)
);
CREATE UNIQUE INDEX uq_project_follows ON project_follows (project_id, member_id);

CREATE TABLE reviews
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id BIGINT      NOT NULL,
    funding_id BIGINT      NOT NULL,                        -- order-service 참조, FK 아님
    member_id  UUID        NOT NULL,
    content    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reviews_project FOREIGN KEY (project_id) REFERENCES projects (id)
);
CREATE INDEX idx_reviews_project ON reviews (project_id);

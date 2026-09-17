-- ============================================================
-- search-service — 검색/탐색 읽기 모델
-- 원본: docs/SearchERD.md 2. DDL (변경 없이 그대로 반영)
-- ============================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 검색어 부분일치(자모 단위 형태소 분석기 없이도 한글 부분 매칭이 되는) 트라이그램 인덱스용.
-- Elasticsearch 등 별도 검색엔진을 새로 들이지 않기로 한 결정의 전제 — SearchERD.md 설계 결정 1번 참고.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ------------------------------------------------------------
-- 1. categories — project-service categories의 읽기 전용 미러
--    project-service와 동일하게 "시드로만 관리, CRUD API 없음"(project-service CLAUDE.md 원칙 준용).
--    이벤트로 동기화되지 않는다 — SearchERD.md 5-④ 참고.
-- ------------------------------------------------------------
CREATE TABLE categories (
    category_major VARCHAR(50) NOT NULL,
    category_minor VARCHAR(50) NOT NULL,
    display_order  INT NOT NULL DEFAULT 0,
    PRIMARY KEY (category_major, category_minor)
);

-- ------------------------------------------------------------
-- 2. project_documents — 홈피드/카테고리탐색/검색의 "상품" 색인
--    PK를 project-service의 projects.id(BIGINT)로 그대로 쓴다 — 이 테이블 자체가
--    검색 결과 조합용 read model이라 별도 서로게이트 id가 필요 없다
--    (project-service의 project_wish_stats와 동일한 설계).
-- ------------------------------------------------------------
CREATE TABLE project_documents (
    project_id            BIGINT PRIMARY KEY,             -- project-service projects.id 참조, FK 아님
    project_public_id     UUID NOT NULL,                  -- 결과 카드 클릭 시 이동 URL에 사용(project-service public_id와 동일 값)
    seller_id             UUID NOT NULL,                  -- member-service 참조, FK 아님
    seller_display_name   VARCHAR(50),                    -- 스냅샷(이벤트 페이로드로 수신). 갱신 지연 가능 — SearchERD.md 5-⑤ 참고
    title                 VARCHAR(40) NOT NULL,
    thumbnail_url         VARCHAR(500),
    category_major        VARCHAR(50) NOT NULL,
    category_minor        VARCHAR(50) NOT NULL,
    status                VARCHAR(20) NOT NULL
                              CHECK (status IN ('ONGOING','SUCCEEDED','FAILED')),
                              -- project-service의 DRAFT/PENDING_REVIEW는 비공개라 색인 대상이 아니다
                              -- (project-service Project.isPublic()과 동일 기준). 승인 이벤트를 받는
                              -- 순간 처음 이 테이블에 행이 생긴다 — SearchERD.md 5-① 참고.
    goal_amount           BIGINT NOT NULL,
    funding_start_at      TIMESTAMP,
    funding_deadline      TIMESTAMP NOT NULL,
    project_created_at    TIMESTAMP NOT NULL,             -- projects.created_at 원본값(신규순 정렬 기준. search-service 자체 색인 시각이 아님)
    current_amount        BIGINT NOT NULL DEFAULT 0,      -- order-service 펀딩 집계 스냅샷 — SearchERD.md 5-② 참고
    achievement_rate      INT NOT NULL DEFAULT 0,
    participant_count     INT NOT NULL DEFAULT 0,
    funding_stats_synced_at TIMESTAMP,                    -- 위 3개 필드를 마지막으로 갱신한 시각(신선도 표시용)
    wish_count            INT NOT NULL DEFAULT 0,         -- member-service project.wished.v1/unwished.v1 구독 누적치 — SearchERD.md 5-⑤ 참고
    indexed_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- search-service가 이 행을 마지막으로 쓴 시각
    deleted_at            TIMESTAMP                       -- soft delete. 프로젝트 삭제·비공개 전환 시 색인에서 제외(하드 삭제 대신 — 재색인 디버깅 편의)
);
CREATE UNIQUE INDEX uq_project_documents_public_id ON project_documents (project_public_id);
CREATE INDEX idx_project_documents_category ON project_documents (category_major, category_minor) WHERE deleted_at IS NULL;
CREATE INDEX idx_project_documents_status ON project_documents (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_project_documents_seller ON project_documents (seller_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_project_documents_deadline ON project_documents (funding_deadline) WHERE deleted_at IS NULL AND status = 'ONGOING'; -- 마감임박순
CREATE INDEX idx_project_documents_created ON project_documents (project_created_at DESC) WHERE deleted_at IS NULL; -- 신규순
CREATE INDEX idx_project_documents_popularity ON project_documents (participant_count DESC, wish_count DESC) WHERE deleted_at IS NULL; -- 인기순(가정 — SearchERD.md 5-⑥ 참고)
-- 키워드 부분일치 검색(제목 + 판매자명). GIN + pg_trgm으로 ILIKE '%keyword%'류 질의에 인덱스가 타게 한다.
CREATE INDEX idx_project_documents_title_trgm ON project_documents USING GIN (title gin_trgm_ops) WHERE deleted_at IS NULL;
CREATE INDEX idx_project_documents_seller_name_trgm ON project_documents USING GIN (seller_display_name gin_trgm_ops) WHERE deleted_at IS NULL;

-- ------------------------------------------------------------
-- 3. live_documents — 검색 "LIVE" 탭 + 홈 진행중 LIVE 배너 색인
--    ⚠️ live-service가 아직 개발에 착수하지 않아(settings.gradle 서비스 배열 미포함, 담당자도 미정 —
--    notification-service NOTI-002 검토의견과 동일한 근거) 이 테이블을 채울 이벤트 자체가 없다.
--    스키마는 PRD 10.1/10.3/11.1 요구사항을 기준으로 선반영만 해둔다 — SearchERD.md 5-③ 참고.
-- ------------------------------------------------------------
CREATE TABLE live_documents (
    live_id            BIGINT PRIMARY KEY,                -- live-service 내부 id 참조(가칭), FK 아님
    live_public_id      UUID NOT NULL,
    project_id          BIGINT NOT NULL,                  -- 연동된 project-service 프로젝트, FK 아님
    project_public_id   UUID NOT NULL,
    seller_id           UUID NOT NULL,
    seller_display_name VARCHAR(50),
    title               VARCHAR(100) NOT NULL,
    thumbnail_url       VARCHAR(500),
    status              VARCHAR(20) NOT NULL
                             CHECK (status IN ('SCHEDULED','LIVE','ENDED')),
    scheduled_at        TIMESTAMP,                        -- 예정 LIVE
    started_at          TIMESTAMP,
    ended_at            TIMESTAMP,
    viewer_count        INT NOT NULL DEFAULT 0,           -- 실시간 시청자 수(홈 배너·목록 뱃지용)
    indexed_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP
);
CREATE UNIQUE INDEX uq_live_documents_public_id ON live_documents (live_public_id);
CREATE INDEX idx_live_documents_status ON live_documents (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_live_documents_title_trgm ON live_documents USING GIN (title gin_trgm_ops) WHERE deleted_at IS NULL;

-- ------------------------------------------------------------
-- 4. search_query_logs — 실행된 모든 검색의 원본 로그
--    popular_search_keywords 배치 집계의 소스. 개인화(향후 AI 추천) 재료로도 재사용 가능.
-- ------------------------------------------------------------
CREATE TABLE search_query_logs (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id    UUID,                                    -- 비로그인 허용이라 NULL 가능 — SearchERD.md 5-⑦ 참고
    keyword      VARCHAR(100) NOT NULL,
    result_count INT NOT NULL,
    searched_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_search_query_logs_keyword_time ON search_query_logs (keyword, searched_at DESC);
CREATE INDEX idx_search_query_logs_time ON search_query_logs (searched_at DESC); -- 집계 배치가 최근 N시간만 스캔

-- ------------------------------------------------------------
-- 5. recent_search_keywords — 회원별 최근 검색어(로그인 회원 한정 — SearchERD.md 5-⑦ 참고)
--    같은 키워드 재검색 시 upsert로 searched_at만 갱신(멱등) — member-service wishes와 동일한
--    "재등록해도 에러 없이 최신 상태만 갱신" 패턴.
-- ------------------------------------------------------------
CREATE TABLE recent_search_keywords (
    member_id   UUID NOT NULL,
    keyword     VARCHAR(100) NOT NULL,
    searched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id, keyword)
);
CREATE INDEX idx_recent_search_keywords_member ON recent_search_keywords (member_id, searched_at DESC);
-- 보관 개수 상한(정책값, SearchERD.md 5-⑧)은 애플리케이션에서 upsert 후 "member_id당 N개 초과분 삭제" 배치/쿼리로 처리.
-- UNIQUE 제약만으로는 개수 제한을 걸 수 없어 DB 레벨 강제는 하지 않는다.

-- ------------------------------------------------------------
-- 6. popular_search_keywords — 전역 인기 검색어 "현재 스냅샷"
--    배치가 매 주기 TRUNCATE 후 재적재하는 단순 스냅샷 테이블(이력 보관 안 함 — 필요해지면 별도 테이블 분리).
-- ------------------------------------------------------------
CREATE TABLE popular_search_keywords (
    rank          INT PRIMARY KEY,
    keyword       VARCHAR(100) NOT NULL,
    search_count  INT NOT NULL,
    aggregated_at TIMESTAMP NOT NULL
);

-- ------------------------------------------------------------
-- 7. seller_summary — 검색 "판매자" 탭용 집계 뷰
--    별도 동기화 테이블을 두지 않는다. project_documents가 이미 seller_id·seller_display_name을
--    갖고 있어 그룹핑만으로 충분하고, 물리 테이블로 중복하면 갱신 누락 위험만 늘어난다
--    (member-service CLAUDE.md의 "복사하면 동기화 문제만 새로 생긴다" 설계 메모와 같은 이유).
-- ------------------------------------------------------------
CREATE VIEW seller_summary AS
SELECT
    seller_id,
    (ARRAY_AGG(seller_display_name ORDER BY indexed_at DESC))[1] AS seller_display_name, -- 가장 최근에 갱신된 스냅샷
    COUNT(*) FILTER (WHERE status = 'ONGOING')                    AS ongoing_project_count,
    COUNT(*)                                                       AS total_project_count,
    MAX(indexed_at)                                                AS last_indexed_at
FROM project_documents
WHERE deleted_at IS NULL
GROUP BY seller_id;

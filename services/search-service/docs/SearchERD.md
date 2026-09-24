# Search 도메인 ERD

> `search-service`의 DB 스키마입니다. 루트 `docs/PRD.md`(10. 홈, 10.2 카테고리 탐색, 10.3 검색), 공통 규칙(`.claude/rules/`)을 전제로 합니다.
> 다른 도메인 ERD(`OrderPayment_ERD_검토및수정.md`, `FulfillmentERD.md`)와 동일하게 DDL + 설계 결정 + 확인 필요 사항 순으로 정리합니다.

## 0. 이 서비스의 DB가 다른 서비스와 다른 이유

지금까지 나온 도메인(주문/결제/배송)은 전부 **자기 도메인의 원본 데이터**를 갖는 서비스였습니다. `search-service`는 다릅니다 — **원본 데이터를 하나도 소유하지 않습니다.** 프로젝트 원본은 project-service, 판매자 정보는 member-service, LIVE는 (아직 없는) live-service, 펀딩 집계는 order-service 소유입니다.

`search-service`가 하는 일은 이 원본들의 **읽기 전용 비정규화 사본(read model)** 을 자기 DB에 만들어두고, 홈피드·카테고리 탐색·키워드 검색이 매 요청마다 다른 서비스를 동기 호출하지 않고 이 사본 하나만 조회해서 응답하게 하는 것입니다. 그래서 이 문서의 테이블은 전부 "무엇으로 채워지는가(이벤트/배치)"가 핵심이고, 그 채우는 경로가 없으면 테이블은 영원히 빈 채로 남습니다 — order-service의 `inventories`(ORDER-016 전까지 항상 비어 있던 문제)·fulfillment-service의 `fulfillment_trackers`와 같은 종류의 함정이 여기도 그대로 있습니다. **1번 "설계 결정 사항"과 5번 "남은 확인 필요 사항"을 먼저 읽어주세요** — 이 서비스는 그 두 섹션이 본문 DDL보다 중요합니다.

DB-per-service 원칙(CLAUDE.md)에 따라 물리적으로 분리된 자체 PostgreSQL을 쓰며, 다른 서비스 테이블에 FK를 걸지 않습니다("참조, FK 아님" 주석 처리는 기존 도메인과 동일 컨벤션).

## 1. 테이블 개요

| 테이블 | 용도 | 채우는 방법 |
| --- | --- | --- |
| `categories` | 카테고리 대/중분류 마스터(트리 탐색용) | project-service `categories`와 동일한 시드를 별도 관리(이벤트 없음 — 3번 참고) |
| `project_documents` | 홈피드·카테고리·검색의 "상품(프로젝트)" 색인 | project-service 프로젝트 이벤트 구독(**신설 필요**, 5-①) + order-service 펀딩 집계 동기화(5-②) + member-service 찜 이벤트 구독 |
| `live_documents` | (미사용) 검색 "LIVE" 탭 색인으로 선반영했던 테이블 | **쓰지 않는다** — LIVE 탭·홈 배너 모두 live-service 공개 API 프록시로 해결(5-③) |
| `search_query_logs` | 실행된 모든 검색 원본 로그 | 검색 API 호출 시마다 자체 기록 |
| `recent_search_keywords` | 회원별 최근 검색어 | 검색 API 호출 시마다 자체 upsert |
| `popular_search_keywords` | 전역 인기 검색어 상위 N (스냅샷) | `search_query_logs` 집계 배치(주기 정책 확인 필요) |
| `seller_summary`(VIEW) | 검색의 "판매자" 탭 | `project_documents`를 seller_id로 집계(별도 동기화 불필요) |

---

## 2. DDL

```sql
-- ============================================================
-- search-service — 검색/탐색 읽기 모델
-- ============================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 검색어 부분일치(자모 단위 형태소 분석기 없이도 한글 부분 매칭이 되는) 트라이그램 인덱스용.
-- Elasticsearch 등 별도 검색엔진을 새로 들이지 않기로 한 결정의 전제 — 3번 설계 결정 참고.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ------------------------------------------------------------
-- 1. categories — project-service categories의 읽기 전용 미러
--    project-service와 동일하게 "시드로만 관리, CRUD API 없음"(project-service CLAUDE.md 원칙 준용).
--    이벤트로 동기화되지 않는다 — 5-④ 참고.
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
    seller_display_name   VARCHAR(50),                    -- 스냅샷(이벤트 페이로드로 수신). 갱신 지연 가능 — 5-⑤ 참고
    title                 VARCHAR(40) NOT NULL,
    thumbnail_url         VARCHAR(500),
    category_major        VARCHAR(50) NOT NULL,
    category_minor        VARCHAR(50) NOT NULL,
    status                VARCHAR(20) NOT NULL
                              CHECK (status IN ('ONGOING','SUCCEEDED','FAILED')),
                              -- project-service의 DRAFT/PENDING_REVIEW는 비공개라 색인 대상이 아니다
                              -- (project-service Project.isPublic()과 동일 기준). 승인 이벤트를 받는
                              -- 순간 처음 이 테이블에 행이 생긴다 — 5-① 참고.
    goal_amount           BIGINT NOT NULL,
    funding_start_at      TIMESTAMP,
    funding_deadline      TIMESTAMP NOT NULL,
    project_created_at    TIMESTAMP NOT NULL,             -- projects.created_at 원본값(신규순 정렬 기준. search-service 자체 색인 시각이 아님)
    current_amount        BIGINT NOT NULL DEFAULT 0,      -- order-service 펀딩 집계 스냅샷 — 5-② 참고
    achievement_rate      INT NOT NULL DEFAULT 0,
    participant_count     INT NOT NULL DEFAULT 0,
    funding_stats_synced_at TIMESTAMP,                    -- 위 3개 필드를 마지막으로 갱신한 시각(신선도 표시용)
    wish_count            INT NOT NULL DEFAULT 0,         -- member-service project.wished.v1/unwished.v1 구독 누적치 — 5-⑤ 참고
    indexed_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- search-service가 이 행을 마지막으로 쓴 시각
    deleted_at            TIMESTAMP                       -- soft delete. 프로젝트 삭제·비공개 전환 시 색인에서 제외(하드 삭제 대신 — 재색인 디버깅 편의)
);
CREATE UNIQUE INDEX uq_project_documents_public_id ON project_documents (project_public_id);
CREATE INDEX idx_project_documents_category ON project_documents (category_major, category_minor) WHERE deleted_at IS NULL;
CREATE INDEX idx_project_documents_status ON project_documents (status) WHERE deleted_at IS NULL;
CREATE INDEX idx_project_documents_seller ON project_documents (seller_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_project_documents_deadline ON project_documents (funding_deadline) WHERE deleted_at IS NULL AND status = 'ONGOING'; -- 마감임박순
CREATE INDEX idx_project_documents_created ON project_documents (project_created_at DESC) WHERE deleted_at IS NULL; -- 신규순
CREATE INDEX idx_project_documents_popularity ON project_documents (participant_count DESC, wish_count DESC) WHERE deleted_at IS NULL; -- 인기순(가정 — 5-⑥ 참고)
-- 키워드 부분일치 검색(제목 + 판매자명). GIN + pg_trgm으로 ILIKE '%keyword%'류 질의에 인덱스가 타게 한다.
CREATE INDEX idx_project_documents_title_trgm ON project_documents USING GIN (title gin_trgm_ops) WHERE deleted_at IS NULL;
CREATE INDEX idx_project_documents_seller_name_trgm ON project_documents USING GIN (seller_display_name gin_trgm_ops) WHERE deleted_at IS NULL;

-- ------------------------------------------------------------
-- 3. live_documents — ⚠️ 현재 사용하지 않는 테이블이다.
--    검색 "LIVE" 탭과 홈 LIVE 섹션 모두 live-service 공개 API(GET /api/v1/lives,
--    GET /api/v1/lives/banner) 프록시로 해결했다. 이벤트 페이로드에 카드 필드가 없고
--    SCHEDULED 전이 이벤트도 없어 색인으로는 만들 수 없기 때문이다 — 5-③ 참고.
--    아래 스키마는 선반영 상태 그대로 남겨둔 것이다(title NOT NULL은 실제 LIVE와 불일치).
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
    member_id    UUID,                                    -- 비로그인 허용이라 NULL 가능 — 5-⑦ 참고
    keyword      VARCHAR(100) NOT NULL,
    result_count INT NOT NULL,
    searched_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_search_query_logs_keyword_time ON search_query_logs (keyword, searched_at DESC);
CREATE INDEX idx_search_query_logs_time ON search_query_logs (searched_at DESC); -- 집계 배치가 최근 N시간만 스캔

-- ------------------------------------------------------------
-- 5. recent_search_keywords — 회원별 최근 검색어(로그인 회원 한정 — 5-⑦ 참고)
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
-- 보관 개수 상한(정책값, 5-⑧)은 애플리케이션에서 upsert 후 "member_id당 N개 초과분 삭제" 배치/쿼리로 처리.
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
--    (member-service CLAUDE.md의 "복사하면 동기화 문제만 새로 생긴다" follows 설계 메모와 같은 이유).
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
```

---

## 3. 설계 결정 사항

1. **검색엔진으로 Elasticsearch/OpenSearch를 새로 들이지 않고 PostgreSQL(`pg_trgm`)로 시작한다[가정].** 이 레포의 다른 모든 서비스가 Postgres 단일 스택이고(`config-convention.md`), 새 인프라 도입은 K8s/CNPG 운영 부담을 늘린다. `pg_trgm`은 형태소 분석 없이도 부분일치·오탈자 허용 검색이 어느 정도 되고, MVP 트래픽 규모에서는 충분하다고 가정한다. **검색 정확도·트래픽이 커지는 시점에 Elasticsearch 도입을 재검토한다** — payment-service 문서가 정산 분리 여부를 같은 방식으로 열어둔 것과 동일한 태도. PM/인프라팀 확인 필요.
2. **`project_documents`는 project-service 원본의 부분 복제(비정규화 읽기 모델)다.** 원본을 절대 직접 쓰지 않고, 오직 이벤트 구독으로만 갱신한다(DB-per-service). 이 서비스가 원본과 다르게 가질 수 있는 값은 "갱신 이벤트가 아직 도착하지 않아 생기는 지연"뿐이며, 이는 이 종류의 read-model 서비스에서 항상 감수하는 최종적 일관성(eventual consistency)이다.
3. **PK를 project-service의 내부 id(BIGINT)로 그대로 쓴다.** `project_wish_stats`(project-service)와 같은 이유 — 이 테이블 자체가 이미 "다른 서비스 데이터의 읽기 전용 사본"이라 별도 서로게이트 키가 주는 이점(외부 비노출 등)이 없다. 외부 노출·URL 생성에는 `project_public_id`를 쓴다.
4. **`seller_display_name`은 이벤트 페이로드로 받는 스냅샷이며 최신 값과 다를 수 있다.** project-service가 이미 같은 문제를 안고 있다 — `SellerProfileClient.getDisplayName()`으로 매번 동기 조회하는 대신 이벤트 페이로드에 실어보내는 쪽을 채택한다고 가정한다(정확히는 project-service 담당자와 이벤트 스키마 협의 필요, 5-① 참고). member-service `wishes.project_title`이 이벤트 부재로 계속 `null`인 것과 유사한 종류의 절충이며, 여기서는 "약간 오래된 판매자명"이 "색인 완전 실패"보다 낫다고 판단한다.
5. **`categories`는 project-service `categories`와 내용이 같아야 하는 완전히 별도의 물리 테이블이다.** 마스터 데이터 변경 이벤트가 이 레포 어디에도 없어(project-service 자신도 "시드로만 관리") 이벤트 동기화 대상이 아니다 — Flyway 시드 스크립트를 project-service와 맞춰 관리하는 수동 동기화를 가정한다.
6. **`project_display_code`(예: `F0000123`)는 컬럼으로 저장하지 않는다.** project-service의 생성 컬럼(`'F' || LPAD(id::text, 7, '0')`)과 동일한 규칙을 API 응답 직렬화 시점에 `project_id`로부터 그대로 계산한다 — 원본과 100% 결정론적으로 같은 값이 나오는 파생값을 굳이 이벤트로 실어보내거나 별도 컬럼에 중복 저장할 이유가 없다.
7. **인기순 정렬은 `participant_count DESC, wish_count DESC`로 가정한다.** PRD 10.3.3은 "인기순" 정렬 기준 존재만 명시하고 산출식을 정의하지 않는다 — PM 확인 전까지의 기본값이다.
8. **`search_query_logs.member_id`는 NULL을 허용한다.** PRD가 검색 자체는 로그인 요건을 명시하지 않아 비로그인 검색을 허용한다고 가정한다(회원 전용 기능은 최근 검색어 저장뿐). 인기 검색어 집계는 로그인 여부와 무관하게 전체 로그를 사용한다.

---

## 4. 다른 서비스 스키마와의 관계 요약

| search-service 필드 | 원본 소유 서비스 | 원본 필드 | 동기화 경로 |
| --- | --- | --- | --- |
| `project_documents.title/thumbnail_url/category_*/goal_amount/funding_*` | project-service | `projects.*` | 신규 이벤트 필요 — 5-① |
| `project_documents.seller_display_name` | member-service (경유: project-service 스냅샷) | `members.nickname` | project-service 이벤트 페이로드에 얹혀 전달(신설) |
| `project_documents.current_amount/achievement_rate/participant_count` | order-service | 펀딩 집계 | project-service의 `funding_status_snapshots`와 동일한 문제 — 5-② |
| `project_documents.wish_count` | member-service | `wishes` 카운트 | 기존 `project.wished.v1`/`project.unwished.v1` 재구독(신규 컨슈머 그룹) |
| `live_documents.*` | live-service | — | 미사용 테이블 — 동기화하지 않는다(5-③) |

---

## 5. ⚠️ 남은 확인 필요 사항 (신규 발견 포함 — 우선순위순)

- **① [신규 발견, 최우선, ORDER-016과 동일 성격] project-service가 "프로젝트가 공개됐다/바뀌었다"는 이벤트를 전혀 발행하지 않는다.** `event-convention.md`의 토픽 목록을 보면 project-service가 발행하는 이벤트는 `reward.created.v1`/`reward.updated.v1`(리워드 단위)과 `project.funding-deadline-reached.v1`(마감 트리거)뿐이다. **프로젝트 자체의 승인(공개)·정보 수정 이벤트가 없다.** `search-service`의 존재 이유가 "프로젝트를 색인해 보여주는 것"인데, 정작 색인할 프로젝트가 생기는 순간(PROJECT-030 심사 승인, DRAFT/PENDING_REVIEW → ONGOING)을 알 방법이 없다 — 이 이벤트가 없으면 `project_documents`는 영원히 비어 있고 검색·홈피드·카테고리 탐색 전부가 항상 빈 결과만 반환한다. **project-service 담당자와 최우선으로 협의해 다음 이벤트 신설이 필요하다** (event-convention.md에도 반영 필요):
  - `project.approved.v1`(또는 `project.published.v1`) — payload: `projectId, publicId, sellerId, sellerDisplayName, title, thumbnailUrl, categoryMajor, categoryMinor, goalAmount, fundingStartAt, fundingDeadline, createdAt`. 파티션 키 `projectId`.
  - `project.updated.v1` — 공개 후 제목/썸네일/카테고리 등이 바뀔 수 있다면 필요(정책 확인 필요 — 승인 후 수정 자체가 허용되는지부터 project-service 쪽 확인 필요).
  - 프로젝트 종료(성립/미달) 자체는 order-service가 이미 발행하는 `funding.succeeded.v1`/`funding.goal-failed.v1`을 그대로 재사용해 `status`를 `SUCCEEDED`/`FAILED`로 전이하면 된다(신규 이벤트 불필요).
- **② [신규 발견] `current_amount`/`achievement_rate`/`participant_count`를 채울 이벤트도 없다.** project-service조차 자기 몫(PROJECT-015 펀딩 현황 조회)을 위해 이 문제를 풀어야 했고, 그 문서는 "order-service가 발행하는 펀딩 집계 이벤트를 구독(갱신 주기 1일)"이라고만 적혀 있을 뿐 `event-convention.md` 토픽 목록엔 해당 이벤트가 없다 — **즉 project-service도 아직 이 이벤트의 정확한 스펙을 확정하지 못한 상태로 보인다.** search-service는 이 값 없이는 "인기순" 정렬도, 카드에 달성률을 보여주는 것도 불가능하다. project-service 담당자가 이 이벤트를 확정하는 시점에 **search-service도 같은 이벤트를 구독하도록(팬아웃 컨슈머 그룹 추가) 함께 챙겨야 한다** — 새로 별도 이벤트를 만들 필요 없이 project-service가 만들 이벤트에 얹혀가면 된다.
- **③ [해소] LIVE는 색인 대신 live-service 공개 API를 직접 부른다.** 홈 LIVE 섹션은 `GET /api/v1/lives/banner`, 검색 LIVE 탭은 `GET /api/v1/lives`를 프록시한다(`LiveCardClient`). `live_documents` 컨슈머를 만들지 않은 이유는 미룬 게 아니라 **이벤트로는 카드를 만들 수 없어서**다 — `live.started.v1`/`live.ended.v1` 페이로드가 `{liveId, projectId, occurredAt, projectTitle}` 뿐이라 `introText`·`thumbnailUrl`·`scheduledStartAt`·`likeCount`가 없고, SCHEDULED 전이 이벤트가 아예 없어 예정 LIVE는 영원히 색인되지 않으며, `live_documents.title`은 `NOT NULL`인데 LIVE에는 제목 입력 자체가 없다. LIVE 건수는 프로젝트와 자릿수가 달라 조회마다 동기 호출해도 된다 — 지연이 실제로 문제가 되면 그때 이벤트 확장 + 컨슈머를 같이 붙인다. **`live_documents` 테이블은 당분간 비어 있는 채로 둔다**(드롭 여부는 LIVE 검색 요구가 확정된 뒤 결정).
- **④ `categories` 전체 체계가 미확정이다.** project-service의 실제 시드(`V3__seed_categories.sql`)는 "테크·가전/패션·잡화/푸드/뷰티/홈리빙" 5개 대분류의 최소 테스트 세트이며, PRD 4.2.4가 정의한 7개 대분류(테크·가전/홈·리빙/뷰티/패션/푸드/스포츠/캐릭터·굿즈) 및 세부 상세 카테고리와 표기가 다르다(예: "홈리빙" vs "홈·리빙"). **search-service의 카테고리 탐색(SEARCH-003/004)은 project-service가 실제로 쓰는 카테고리 표기와 정확히 일치해야 필터링이 맞는다** — 전체 카테고리 체계 확정 및 project-service·search-service 간 시드 동기화 방법(수동 복사 vs 공용 시드 파일)을 PM/project-service 담당자와 확인해야 한다.
- **⑤ `seller_display_name`/`wish_count` 갱신 지연 허용 범위 확인 필요.** 3번 설계 결정 참고 — 회원이 닉네임을 바꾼 뒤 검색 결과에 반영되기까지 걸리는 시간에 대한 별도 SLA 요구가 없다는 전제다.
- **⑥ 인기순 정렬 산출식 PM 확인 필요.** 설계 결정 7번의 `participant_count DESC, wish_count DESC`는 가정값이다. 조회수(클릭)를 반영할지, 가중치를 둘지는 미정.
- **⑦ 비로그인 검색·최근 검색어 정책 확인 필요.** 최근 검색어는 회원 전용으로 가정했다(비로그인 사용자는 클라이언트 로컬 저장만 하고 서버 API를 호출하지 않는다고 가정). 서버가 비로그인 사용자의 "최근 검색어"까지 book-keeping해야 한다면 `recent_search_keywords.member_id`를 device/session 식별자까지 허용하도록 다시 설계해야 한다.
- **⑧ 정책값 미확정**: 최근 검색어 회원당 보관 개수(기본값 제안: 10개), 인기 검색어 집계 윈도우·갱신 주기(기본값 제안: 최근 24시간 집계, 1시간마다 배치 갱신), 인기 검색어 노출 개수(기본값 제안: 10개) — 전부 PM 확인 필요, 위 DDL·기능명세서는 이 기본값을 전제로 작성했다.
- **⑨ `config-convention.md`에 search-service의 로컬 DB/앱 포트 슬롯이 없다.** 기존 표는 auth(5432/8081)~notification(5439/8088)까지만 있다 — **DB 포트 `5440`, 앱 포트 `8089`를 새로 추가해야 한다**(이 문서·API 명세서는 이 값을 가정하고 작성). `settings.gradle`의 서비스 배열에도 `search-service` 추가 필요.

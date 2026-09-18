-- ============================================================
-- 6. live-service (별도 서버/DB)
--
-- 채널(영구 자원)과 세션(개별 방송)을 분리해서 모델링한다 — Amazon IVS의
-- Channel/Stream 리소스 모델과 대응된다. 채널은 판매자가 한 번 만들면
-- 여러 방송에서 재사용하고, playback_url·ingest_endpoint·stream_key는
-- 전부 채널 레벨에서 발급되어 세션마다 바뀌지 않는다.
--
-- 스키마는 나누지 않는다(초안의 streaming/chat 분리 폐기).
-- "영상 장애가 채팅까지 막으면 안 된다"는 요구는 같은 DB 인스턴스 안에서
-- 스키마만 나눠서는 얻어지지 않는다 — Flyway schemas 설정과 크로스스키마
-- FK만 늘고 실제 격리는 0이다. 진짜 격리가 필요해지면 그때 DB를 분리한다.
-- 나머지 7개 서비스도 전부 단일 스키마다.
-- ============================================================

-- updated_at 자동 갱신 함수. 트리거보다 먼저 정의해야 한다 —
-- member-service V1에서 이 함수 없이 트리거만 걸었다가 Flyway 마이그레이션이
-- 통째로 실패한 전례가 있다.
CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ------------------------------------------------------------
-- 채널 — 판매자당 1개, 영구 자원
-- ------------------------------------------------------------
CREATE TABLE live_channels
(
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seller_id           UUID         NOT NULL,          -- member-service 참조, FK 아님(서비스 분리)
    ivs_channel_arn     VARCHAR(255) NOT NULL,
    ivs_ingest_endpoint VARCHAR(255) NOT NULL,
    ivs_playback_url    TEXT         NOT NULL,
    -- ⚠️ 스트림 키는 탈취되면 타인이 이 채널로 무단 송출할 수 있는 민감정보다.
    --    평문 저장 금지 — 여기엔 Secrets Manager 등 비밀관리 시스템의 "참조 식별자"만
    --    저장하고 실제 키 값은 DB 밖에 둔다(security.md S9).
    ivs_stream_key_ref  VARCHAR(255),
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 판매자당 채널 1개 = 동시에 두 방송을 송출할 수 없다는 뜻이다.
-- PRD 6.1.3의 "동일 프로젝트 반복 LIVE 개설"은 순차 진행이라 이 제약으로 충분하다.
-- 동시 송출 요구가 실제로 생기면 이 UNIQUE를 풀고 채널 풀 관리를 도입한다.
CREATE UNIQUE INDEX uq_live_channels_seller ON live_channels (seller_id);


-- ------------------------------------------------------------
-- 세션 — 개별 방송. 채널 위에서 벌어지는 일시적 이벤트
-- ------------------------------------------------------------
CREATE TABLE live_sessions
(
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 외부 노출용 UUID. API 경로(/api/v1/lives/{liveId})와 공유 링크가 이 값을 쓴다.
    -- 내부 PK(BIGINT)를 URL에 노출하면 전체 방송 수가 추측된다.
    public_id          UUID         NOT NULL,
    -- project-service 내부 PK(BIGINT). 외부 API는 projects.public_id(UUID)를 받고
    -- 서비스가 변환해서 저장한다 — 초안 API 예시가 UUID인데 컬럼이 BIGINT라
    -- 타입이 어긋나 있던 지점이다.
    project_id         BIGINT       NOT NULL,           -- project-service 참조, FK 아님
    channel_id         BIGINT       NOT NULL,           -- 같은 DB, 실제 FK
    category_major     VARCHAR(30),                     -- 연결 프로젝트 값이 기본, 개별 덮어쓰기 가능
    category_minor     VARCHAR(30),
    intro_text         VARCHAR(200),                    -- LIVE 소개 문구(PRD 6.2.4.1)
    thumbnail_url      TEXT,
    scheduled_start_at TIMESTAMPTZ,                     -- 판매자가 예약한 시각. 즉시 시작이면 NULL
    actual_start_at    TIMESTAMPTZ,                     -- IVS가 실제 송출을 시작한 시각
    actual_end_at      TIMESTAMPTZ,
    status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'SCHEDULED', 'LIVE', 'ENDED', 'ERROR')),
    -- VOD. 초안에는 컬럼이 없어 GET /vod가 돌려줄 값이 어디에도 없었다.
    -- 만료 서명은 매 요청마다 새로 붙이므로 여기엔 원본 경로만 저장한다.
    vod_url            TEXT,
    vod_ready_at       TIMESTAMPTZ,
    like_count         INT          NOT NULL DEFAULT 0, -- live_likes 집계 캐시(목록 조회용)
    -- IVS Chat Room ARN. 세션당 1개라 별도 테이블로 빼지 않는다 —
    -- 테이블로 만들면 실질 정보는 이 문자열 하나인데 id·FK·인덱스가 따라붙고,
    -- 채팅 적재에서 roomArn → session_id 변환이 조인이 된다. 컬럼이면 단일 조회다.
    ivs_chat_room_arn  VARCHAR(255),
    error_detail       TEXT,                            -- 송출 오류 안내(PRD 6.3.4)
    error_occurred_at  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_live_sessions_channel FOREIGN KEY (channel_id) REFERENCES live_channels (id)
);

-- DRAFT를 둔 이유: LIVE 생성(요구사항정의서 6.1.4)과 설정 등록(요구사항정의서 6.2.4.1)이 별도 단계라,
-- 생성 직후엔 방송 예정일시가 없다. scheduled_start_at을 NOT NULL로 두면
-- 생성 API가 값을 지어내야 한다.
CREATE TRIGGER trg_live_sessions_updated_at
    BEFORE UPDATE ON live_sessions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE UNIQUE INDEX uq_live_sessions_public_id ON live_sessions (public_id);
CREATE INDEX idx_live_sessions_project ON live_sessions (project_id);
-- 소비자 목록 조회(요구사항정의서 11.1.4)가 상태별 + 예정시각순이라 복합으로 건다.
CREATE INDEX idx_live_sessions_status_scheduled ON live_sessions (status, scheduled_start_at DESC);


-- ------------------------------------------------------------
-- AI 큐시트(요구사항정의서 6.2.4.2)
-- ------------------------------------------------------------
-- 초안 ERD에 없던 테이블이다. POST로 생성 요청만 하고 GET으로 읽을 곳이
-- 없어 비동기 조회가 성립하지 않았다.
--
-- 세션당 1행으로 둔다 — 재생성은 덮어쓰기이고, 이력을 보관하라는 요구가 없다.
-- status를 PK 옆에 두는 이유: GENERATING 상태면 중복 생성 요청을 409로 막는다
-- (PRD 6.2.4.2 "생성 중 중복 요청 방지").
CREATE TABLE live_cue_sheets
(
    session_id        BIGINT      NOT NULL PRIMARY KEY,
    mode              VARCHAR(20) NOT NULL CHECK (mode IN ('SCENARIO', 'SCRIPT')),
    status            VARCHAR(20) NOT NULL DEFAULT 'GENERATING'
        CHECK (status IN ('GENERATING', 'COMPLETED', 'FAILED')),
    target_duration_sec INT       NOT NULL DEFAULT 600,  -- PRD 6.2.3: 10분 이내
    -- 구간 배열을 JSONB로 둔다. 구간은 항상 큐시트 전체와 함께 읽고 쓰며,
    -- 구간 단위로 조회·조인할 일이 없다 — 테이블로 쪼개면 order 재정렬마다
    -- 행 재배치가 생기고 얻는 게 없다.
    -- 형태: [{"order":1,"title":"오프닝","summary":"...","estimatedSec":60,"script":"..."}]
    segments          JSONB,
    failure_reason    TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_live_cue_sheets_session FOREIGN KEY (session_id) REFERENCES live_sessions (id)
);

CREATE TRIGGER trg_live_cue_sheets_updated_at
    BEFORE UPDATE ON live_cue_sheets
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ------------------------------------------------------------
-- 좋아요(요구사항정의서 11.2.4)
-- ------------------------------------------------------------
-- 초안 ERD에 없었다. PUT /lives/{liveId}/like가 쓸 저장소가 없었다.
-- 행의 존재 자체가 좋아요 상태이고 상태 전이가 없는 단순 애그리거트다.
-- PK가 곧 중복 방지 제약이라 idempotent PUT을 ON CONFLICT DO NOTHING으로 처리한다.
CREATE TABLE live_likes
(
    session_id BIGINT      NOT NULL,
    member_id  UUID        NOT NULL,                    -- member-service 참조, FK 아님
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, member_id),
    CONSTRAINT fk_live_likes_session FOREIGN KEY (session_id) REFERENCES live_sessions (id)
);


-- ------------------------------------------------------------
-- LIVE 검증 탭 — 이 서비스에 테이블을 두지 않는다
-- ------------------------------------------------------------
-- project-service가 live_verifications(V4__add_live_verifications.sql)를 이미 갖고 있고,
-- 그 파일 주석이 경계를 정해뒀다:
--   "방송 송출 자체는 live-service 소관이고, project-service는 방송 종료 후 남는
--    질문요약 참조값(question_summary_id, FK 아님)과 판매자 답변만 보관한다."
--
-- 즉 판매자 답변의 소유자는 project-service다. 소비자 LIVE 검증 탭을 그리는 쪽도 그쪽이라,
-- 여기에 같은 걸 또 두면 답변이 두 군데에 생기고 어느 쪽이 정본인지 알 수 없게 된다.
--
-- live-service는 AI 대표질문(live_question_summaries)까지만 갖고,
-- project-service가 그 public_id를 question_summary_id로 참조해 답변을 붙인다.

-- ------------------------------------------------------------
-- 하이라이트 — 타임라인 마커 + 쇼츠 클립(PRD 6.6)
-- ------------------------------------------------------------
-- 초안 ERD에 없어 하이라이트 API 6개가 전부 저장소 없이 설계돼 있었다(초안도 ⚠️로 인정).
--
-- 마커와 클립을 한 테이블에 둔다. 공통 컬럼(구간·라벨·공개여부·통계)이 거의 전부고,
-- 쪼개면 목록 조회·재생성·삭제·공개설정 API가 전부 두 벌이 된다.
-- 다른 건 클립만 갖는 url·caption뿐이라 nullable로 충분하다.
CREATE TABLE live_highlights
(
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id                UUID        NOT NULL,      -- 외부 노출용
    session_id               BIGINT      NOT NULL,
    kind                     VARCHAR(10) NOT NULL CHECK (kind IN ('MARKER', 'CLIP')),
    -- 장면 유형 라벨(PRD 6.6.3). 타임라인에서는 구간 라벨, 쇼츠에서는 클립 분류로 쓴다.
    scene_label              VARCHAR(20) NOT NULL
        CHECK (scene_label IN ('DEMO', 'AUDIENCE_REACTION', 'SPEC', 'PRICE_BENEFIT', 'COMPARISON')),
    title                    VARCHAR(100),
    start_sec                INT         NOT NULL,
    end_sec                  INT,                       -- MARKER는 시점이라 NULL
    clip_url                 TEXT,                      -- CLIP만
    caption                  TEXT,                      -- CLIP 자막만
    -- 자동 생성 결과는 판매자가 확정하기 전까지 소비자에게 보이지 않는다(PRD 6.6.3).
    -- 기본값 FALSE가 그 정책이다 — 기본 TRUE로 두면 검수 전 내용이 새어나간다.
    is_public                BOOLEAN     NOT NULL DEFAULT FALSE,
    generation_status        VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'
        CHECK (generation_status IN ('GENERATING', 'COMPLETED', 'FAILED')),
    -- 성과 통계(요구사항정의서 6.6.4). 조회/클릭은 여기서 세고, 펀딩 전환 기여는 order 집계와 대조한다.
    view_count               INT         NOT NULL DEFAULT 0,
    click_count              INT         NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_live_highlights_session FOREIGN KEY (session_id) REFERENCES live_sessions (id)
);

CREATE TRIGGER trg_live_highlights_updated_at
    BEFORE UPDATE ON live_highlights
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE UNIQUE INDEX uq_live_highlights_public_id ON live_highlights (public_id);
CREATE INDEX idx_live_highlights_session ON live_highlights (session_id, kind);
-- 소비자 화면은 공개된 것만 읽는다 — partial 인덱스로 비공개 행을 아예 스캔에서 뺀다.
CREATE INDEX idx_live_highlights_public ON live_highlights (session_id) WHERE is_public;


-- ------------------------------------------------------------
-- 채팅
-- ------------------------------------------------------------
-- 전송 계층은 Amazon IVS Chat(관리형)을 우선 적용한다. 자체 WebSocket 서버는
-- 보류이며 폐기가 아니다 — 아래 chat_messages는 메시지가 IVS Chat을 통해 오든
-- 자체 서버를 통해 오든 같은 구조로 저장되므로, 전송 계층이 바뀌어도 이 테이블은
-- 그대로 쓴다.
--
-- IVS Chat의 Room은 IVS 비디오 채널과 직접적인 연관관계가 없는 별개 리소스라,
-- 세션↔Room 매핑을 우리가 들고 있어야 한다. IVS Chat의 메시지 검토 핸들러(Lambda)가
-- PRD 11.3.4의 "부적절 메시지 자동 필터링"을 대신 처리할 수 있다.
-- 룸 생성 시 두 가지를 함께 붙인다(ARN은 live_sessions.ivs_chat_room_arn에 저장):
--   1) 메시지 리뷰 핸들러(Lambda) — 부적절 메시지 필터링. FallbackResult는 DENY로 둔다.
--      필터가 죽었을 때 걸러지지 않은 메시지가 퍼지는 것보다 채팅이 막히는 편이 낫다.
--   2) Chat Logging(Kinesis Data Firehose) — 아래 chat_messages 적재 경로.

-- 클라이언트가 IVS Chat에 직접 붙으므로 우리 서버는 메시지를 실시간으로 보지 못한다.
-- 적재는 Chat Logging → Firehose(전파 지연 약 10초) → 내부 수집 엔드포인트로 들어온다.
-- S3(5분)를 쓰지 않는 이유: AI 대표질문 집계가 3분 주기라 5분 지연으로는 맞출 수 없다.
CREATE TABLE chat_messages
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- IVS Chat이 발급한 MessageId. Firehose는 재전송이 가능해서 이 UNIQUE가 없으면
    -- 같은 메시지가 여러 번 쌓인다. 적재는 ON CONFLICT DO NOTHING으로 한다.
    ivs_message_id VARCHAR(64) NOT NULL,
    session_id     BIGINT      NOT NULL,
    sender_id      UUID        NOT NULL,                -- member-service 참조, FK 아님
    content        TEXT        NOT NULL,
    sent_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- AI가 이 메시지를 어느 대표질문으로 묶었는지("대표질문 원본 조회" 원본 조회용).
    -- NULL = 아직 안 묶였거나 질문이 아님.
    -- 한 메시지가 여러 대표질문에 묶이지 않는 1:N이라 매핑 테이블을 두지 않는다.
    -- AI 재분석은 이 컬럼을 UPDATE하면 끝난다.
    question_summary_id BIGINT,
    CONSTRAINT fk_chat_messages_session FOREIGN KEY (session_id) REFERENCES live_sessions (id)
);
CREATE UNIQUE INDEX uq_chat_messages_ivs_id ON chat_messages (ivs_message_id);

-- 다시보기 시간대별 채팅 조회(요구사항정의서 11.4.4)가 "세션 + 시각 범위"라 복합으로 건다.
CREATE INDEX idx_chat_messages_session ON chat_messages (session_id, sent_at);
-- 초안의 priority(NORMAL/PURCHASE_ALERT) 컬럼은 뺐다 — PRD 어디에도 구매 알림
-- 채팅 요구가 없다. 실제로 필요해지면 그때 추가한다.


-- ------------------------------------------------------------
-- AI 대표질문 / 관심사(PRD 6.4.4.3)
-- ------------------------------------------------------------
-- 분석은 AI 파트가 하고 저장만 이 도메인이 한다.
CREATE TABLE live_question_summaries
(
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id              UUID        NOT NULL,        -- API의 questionId
    session_id             BIGINT      NOT NULL,
    topic                  VARCHAR(50),                 -- 관심사 분류(예: 사이즈/색상)
    summary_text           TEXT        NOT NULL,
    related_question_count INT         NOT NULL DEFAULT 0,
    is_answered            BOOLEAN     NOT NULL DEFAULT FALSE,
    -- 답변 본문. 소비자 Q&A 버튼(요구사항정의서 11.3.4)이 "답변들을 모아본다"를 하려면
    -- 채팅으로 흘려보낸 답변을 여기 남겨야 한다 — 채팅 스트림은 지나가면 끝이다.
    -- AI 초안을 판매자가 수정해 전송(SEND)한 최종 문구가 들어간다.
    answer_text            TEXT,
    answered_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_live_question_summaries_session FOREIGN KEY (session_id) REFERENCES live_sessions (id)
);

CREATE TRIGGER trg_live_question_summaries_updated_at
    BEFORE UPDATE ON live_question_summaries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE UNIQUE INDEX uq_live_question_summaries_public_id ON live_question_summaries (public_id);
-- 판매자 화면과 소비자 Q&A 모달 모두 "질문 발생 건수 내림차순" 단일 기준이다
-- (요구사항정의서 6.4.4.3 / 11.3.4).
CREATE INDEX idx_live_question_summaries_session
    ON live_question_summaries (session_id, related_question_count DESC);

-- 소비자 Q&A 모달(요구사항정의서 11.3.4)은 답변된 질문만 "질문 건수 내림차순"으로 읽는다.
-- 시안이 12건 → 11 → 11 → 11 순으로 나열한다.
CREATE INDEX idx_live_question_summaries_answered
    ON live_question_summaries (session_id, related_question_count DESC) WHERE is_answered;

-- chat_messages.question_summary_id의 FK는 여기서 건다 — 위 테이블이 먼저 만들어져야 한다.
ALTER TABLE chat_messages
    ADD CONSTRAINT fk_chat_messages_question_summary
        FOREIGN KEY (question_summary_id) REFERENCES live_question_summaries (id);
-- 대표질문 하나에 묶인 원본 메시지를 훑는 조회(요구사항정의서 6.4.4.3)용. 안 묶인 행은 제외한다.
CREATE INDEX idx_chat_messages_question_summary
    ON chat_messages (question_summary_id) WHERE question_summary_id IS NOT NULL;

-- ------------------------------------------------------------
-- 라이브 쿠폰 — 이 서비스에 테이블을 두지 않는다
-- ------------------------------------------------------------
-- order-service의 coupons 테이블이 쿠폰 생애주기 전체(발급·클레임·사용처리·복원·정산 차감)를
-- 갖고 있고, LIVE 채널용 컬럼도 이미 들어가 있다:
--   issue_channel('GENERAL'|'LIVE'), live_session_id, drop_type, version(낙관적 락)
-- 재고 차감도 그쪽이 낙관적 락으로 처리한다.
--
-- 그래서 live는 쿠폰을 만들지도 발급하지도 않는다. 대신 order가 "이 방송이 지금 진행 중인가"를
-- 물어볼 수 있도록 GET /internal/v1/lives/{liveId}/status 하나만 제공한다
-- (요구사항정의서 16.6.3 "LIVE 방송 진행 중에만 발급 가능" 검증용).
--
-- 호출 방향이 live→order가 아니라 order→live인 점에 주의. 쿠폰의 주인이 order이므로
-- 판정에 필요한 정보를 order가 가져간다. live가 order 쿠폰 API를 감싸면 위임이 아니라
-- 이중 관리가 된다.


-- ------------------------------------------------------------
-- 이벤트 아웃박스
-- ------------------------------------------------------------
-- member-service member_event_outbox와 같은 형태다. 도메인 쓰기와 같은 트랜잭션에서 적재하고
-- 워커가 발행한다.
--
-- payload를 JSONB로 두는 이유: 질문요약 이벤트가 배열을 실어 보내서 컬럼으로 펼 수 없다.
-- 찜·가입 아웃박스처럼 스칼라 컬럼만 쓰면 이벤트마다 테이블이 하나씩 늘어난다.
CREATE TABLE live_event_outbox
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type      VARCHAR(32) NOT NULL,   -- LIVE_ENDED / LIVE_QUESTIONS_SUMMARIZED
    live_session_id BIGINT      NOT NULL,
    payload         JSONB       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMPTZ,
    attempt_count   INT         NOT NULL DEFAULT 0,
    last_error      TEXT,
    CONSTRAINT fk_live_event_outbox_session FOREIGN KEY (live_session_id) REFERENCES live_sessions (id)
);
CREATE INDEX idx_live_event_outbox_unpublished ON live_event_outbox (id) WHERE published_at IS NULL;


-- ============================================================
-- 초안 ERD에서 의도적으로 제외한 테이블 — 다시 넣지 말 것
-- ============================================================
-- live_notify_requests
--   → notification-service가 이미 소유한다(V2__add_live_notify_requests.sql).
--     알림 발송 주체가 거기라 그쪽이 맞다. 반대로 두면 발송 때마다 live에
--     "누가 신청했나"를 물어야 하고 live가 죽으면 알림도 못 나간다.
--     그쪽 테이블의 live_id UUID는 이 파일의 live_sessions.public_id와 같은 값이다.
--
-- seller_follows
--   → member-service의 follows 테이블과 같은 개념이다(#58에서 구현 완료).
--     "판매자 팔로우"는 회원의 관심 목록이지 방송 도메인 자산이 아니다.
--
-- waiting_queue_entries
--   → PRD 어디에도 대기열 요구사항이 없다. 초안 주석도 같은 지적을 달고 있었다.
--     트래픽 대비는 실제로 막히는 지점이 보일 때 만든다.
--
-- live_coupons
--   → order-service coupons가 쿠폰 생애주기 전체를 소유한다(issue_channel='LIVE' 포함).
--     live는 GET /internal/v1/lives/{liveId}/status로 "진행 중" 여부만 답한다.
--
-- live_verification_posts
--   → project-service live_verifications가 판매자 답변을 소유한다.
--     live는 live_question_summaries까지만 갖고 Kafka로 밀어준다.
--
-- 시청자 수(viewer_count)
--   → 테이블을 두지 않는다. IVS가 채널별 동시 시청자 지표를 제공하므로
--     목록·배너 응답은 그 값을 조회해서 채운다. DB에 실시간 카운터를 두면
--     방송 중 매 초 UPDATE가 들어오고 그만큼 정확해지지도 않는다.

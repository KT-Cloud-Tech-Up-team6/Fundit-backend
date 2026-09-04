-- ============================================================
-- 2. member-service
-- id는 auth-service가 발급한 accounts.id 값을 그대로 사용
-- 2026-09-03 변경: is_foreigner/di_hash/phone_verified_at(본인인증 관련,
-- auth-service로 이관 — auth-service도 현재 미사용) 및 business_type/
-- business_info/seller_verified_at(판매자 심사 관련, member-service 소관
-- 아님) 컬럼 제거. current_mode 컬럼은 추가하지 않음(세션/토큰 클레임으로
-- 관리, DB 미저장). 근거·후순위 항목은 MvpImplementationSummary.md 참고.
-- ============================================================

CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE members (
                         id                UUID NOT NULL PRIMARY KEY,  -- accounts.id 참조값, FK 아님(서비스 분리)
                         name              VARCHAR(50) NOT NULL,             -- 실명, PRD 1.3.4 필수정보
                         phone_number      VARCHAR(20) NOT NULL,             -- 본인인증 성공 여부와 무관하게 입력값 그대로 저장
                         nickname          VARCHAR(50),                      -- PRD에 명시되지 않은 필드, 수정 API는 MVP 범위 밖
                         created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         deleted_at        TIMESTAMPTZ          -- 소프트 딜리트: NULL = 활성 회원. 채우는 쓰기 경로(탈퇴 API/배치)는 아직 없음
);

CREATE TRIGGER trg_members_updated_at
    BEFORE UPDATE ON members
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE INDEX idx_members_active ON members (id) WHERE deleted_at IS NULL;

-- 약관 동의 이력 — 법적으로 이력 보존이 필요해 append-only 로그로 설계
CREATE TABLE terms_agreements (
                                  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                  member_id     UUID NOT NULL,
                                  terms_type    VARCHAR(30) NOT NULL,   -- SERVICE_USE/PRIVACY/AGE_OVER_14/MARKETING/AI_PERSONALIZATION
                                  terms_version VARCHAR(20) NOT NULL,
                                  agreed        BOOLEAN NOT NULL,
                                  agreed_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  CONSTRAINT fk_terms_agreements_member FOREIGN KEY (member_id) REFERENCES members(id)
);
CREATE INDEX idx_terms_agreements_member ON terms_agreements (member_id, terms_type);

-- 찜하기 — Discovery(읽기전용) 대신 Member 도메인 소속으로 확정
-- project_title/project_thumbnail_url은 catalog-service가 발행하는 프로젝트 변경
-- 이벤트를 구독해 동기화하는 스냅샷이다(비정규화). 찜 목록 조회 시 매번
-- catalog-service를 실시간 호출하지 않기 위함 — 서비스 간 결합을 줄이는 대신
-- 프로젝트 정보가 바뀐 뒤 이벤트가 반영되기까지 짧은 지연(최종적 일관성)을 감수한다.
CREATE TABLE wishes (
                        id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        member_id             UUID NOT NULL,
                        project_id            BIGINT NOT NULL,          -- catalog-service 참조, FK 아님
                        project_title         VARCHAR(40),               -- 스냅샷, catalog-service 이벤트로 동기화
                        project_thumbnail_url TEXT,                      -- 스냅샷, catalog-service 이벤트로 동기화
                        snapshot_synced_at    TIMESTAMPTZ,               -- 스냅샷 마지막 동기화 시각
                        created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_wishes_member FOREIGN KEY (member_id) REFERENCES members(id)
);
CREATE UNIQUE INDEX uq_wishes_member_project ON wishes (member_id, project_id);

-- 배송지 — 여러 개 저장 가능(PRD 12.2.3: 등록된 배송지 선택 또는 신규 입력)
CREATE TABLE addresses (
                           id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                           member_id      UUID NOT NULL,
                           recipient_name VARCHAR(50) NOT NULL,
                           phone_number   VARCHAR(20) NOT NULL,
                           zipcode        VARCHAR(10) NOT NULL,
                           address_line1  VARCHAR(200) NOT NULL,
                           address_line2  VARCHAR(200),
                           is_default     BOOLEAN NOT NULL DEFAULT FALSE,
                           created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           CONSTRAINT fk_addresses_member FOREIGN KEY (member_id) REFERENCES members(id)
);
CREATE INDEX idx_addresses_member ON addresses (member_id);
-- ============================================================
-- 1. auth-service (별도 서버/DB)
-- ============================================================

CREATE TABLE accounts
(
    id                   UUID         NOT NULL PRIMARY KEY,    -- Spring에서 생성한 UUID를 그대로 INSERT
    email                VARCHAR(255) NOT NULL,
    password_hash        VARCHAR(255),                         -- 소셜 로그인 전용 계정은 NULL 허용
    social_provider      VARCHAR(20),                          -- KAKAO / GOOGLE, 자체가입은 NULL
    social_id            VARCHAR(255),
    role                 VARCHAR(20)  NOT NULL DEFAULT 'member', -- member / ADMIN
    failed_login_count   SMALLINT     NOT NULL DEFAULT 0,      -- PRD 2.1.3: 5회 실패 시 잠금
    locked_until         TIMESTAMPTZ,                          -- MVP: 5회 실패 시 30분 자동 잠금(고정), 셀프 해제는 P2
    must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,  -- 임시 비밀번호로 재설정된 계정, 다음 로그인 시 변경 강제
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_accounts_email ON accounts (email);
CREATE UNIQUE INDEX uq_accounts_social ON accounts (social_provider, social_id);

-- Refresh Token 저장소 (재발급 시 회전 + 재사용 탐지용)
-- Redis 대신 RDBMS로 시작한다 — DELETE ... RETURNING이 원자적 "확인+즉시폐기"를
-- 대신해주고, expires_at 필터링으로 만료 처리도 가능하다. 트래픽이 늘어 이 조회가
-- 결제/주문 등 비즈니스 쿼리와 자원을 다투는 지점이 실제로 보이면 그때 Redis 이전을
-- 검토한다(지금은 옮길 이유를 미리 만들지 않고, 실제로 필요해지는 순간을 관찰하기로 함).
CREATE TABLE refresh_tokens
(
    token_id   UUID        NOT NULL PRIMARY KEY,
    account_id UUID        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_tokens_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE
);
CREATE INDEX idx_refresh_tokens_account ON refresh_tokens (account_id);

-- 비밀번호 재설정 토큰 저장소 (AUTH-010/AUTH-014)
-- refresh_tokens와 동일한 패턴: 확인+즉시폐기는 DELETE...RETURNING으로 원자적 처리,
-- 1회용이라 재사용 시도는 자연스럽게 거부된다(이미 삭제된 토큰).
CREATE TABLE password_reset_tokens
(
    token_id   UUID        NOT NULL PRIMARY KEY,
    account_id UUID        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_reset_tokens_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE
);
CREATE INDEX idx_password_reset_tokens_account ON password_reset_tokens (account_id);
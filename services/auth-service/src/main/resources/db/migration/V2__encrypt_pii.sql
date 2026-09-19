-- 개인정보 컬럼을 애플리케이션 레벨에서 암호화해 저장한다(security.md S9).
--
-- email은 AES-GCM 암호문을 담는다. IV를 매번 새로 만들어 같은 평문도 매번 다른 암호문이 되므로
-- WHERE email = ? 가 성립하지 않는다 — 조회는 아래 email_hash(HMAC-SHA256)로 한다.
--
-- 결정적 암호화를 쓰지 않는 이유: 같은 값이 같은 암호문이 되어 빈도 분석이 가능해진다.
-- 해시는 복호화가 불가능하므로 본문 보관과 조회 색인을 분리하는 편이 안전하다.

ALTER TABLE accounts
    ALTER COLUMN email TYPE TEXT;          -- Base64 암호문은 평문보다 길다

ALTER TABLE accounts
    ADD COLUMN email_hash CHAR(64),        -- HMAC-SHA256 hex
    -- 이메일 찾기(AUTH-009)가 이름+전화번호로 계정을 찾는다. 원문을 두지 않고 해시만 둔다 —
    -- 복호화가 불가능하므로 개인정보 보관이 아니라 조회 색인이다.
    -- 소셜 전용 계정은 본인인증을 거치지 않은 경로가 있어 NULL을 허용한다.
    ADD COLUMN phone_hash CHAR(64),
    ADD COLUMN name_hash  CHAR(64);

-- 기존 평문 행은 암호문이 아니라 복호화할 수 없다. 개발 데이터뿐이라 지운다 —
-- 남겨두면 로그인 시점에 "복호화 실패"로 터지고 원인을 찾기 어렵다.
DELETE FROM accounts;

ALTER TABLE accounts
    ALTER COLUMN email_hash SET NOT NULL;

DROP INDEX IF EXISTS uq_accounts_email;    -- 평문 기준 UNIQUE. 이제 해시가 그 역할을 한다
CREATE UNIQUE INDEX uq_accounts_email_hash ON accounts (email_hash);
CREATE INDEX idx_accounts_phone_hash ON accounts (phone_hash);

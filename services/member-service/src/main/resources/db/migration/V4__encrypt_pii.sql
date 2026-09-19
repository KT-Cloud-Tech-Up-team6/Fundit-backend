-- 개인정보 컬럼을 애플리케이션 레벨에서 암호화해 저장한다(security.md S9).
-- 변환은 EncryptedStringConverter(JPA AttributeConverter)가 하고, 애플리케이션 코드는 평문만 본다.
--
-- 이 컬럼들로는 검색할 수 없다 — AES-GCM은 IV를 매번 새로 만들어 같은 평문도 매번 다른
-- 암호문이 된다. 조회 조건이 필요해지면 블라인드 인덱스 컬럼을 따로 둬야 한다
-- (auth-service의 email_hash 참고).
--
-- zipcode는 그대로 둔다 — 우편번호 단독으로는 개인을 식별할 수 없고, 배송 권역 집계에 쓴다.

ALTER TABLE members
    ALTER COLUMN name TYPE TEXT,           -- Base64 암호문은 평문보다 길다
    ALTER COLUMN phone_number TYPE TEXT;

ALTER TABLE addresses
    ALTER COLUMN recipient_name TYPE TEXT,
    ALTER COLUMN phone_number TYPE TEXT,
    ALTER COLUMN address_line1 TYPE TEXT,
    ALTER COLUMN address_line2 TYPE TEXT;

-- 기존 평문 행은 암호문이 아니라 복호화할 수 없다. 개발 데이터뿐이라 지운다 —
-- 남겨두면 조회 시점에 "복호화 실패"로 터지고 원인을 찾기 어렵다.
-- auth-service의 V2가 accounts를 비우므로 회원 프로필도 같이 사라져야 짝이 맞는다.
DELETE FROM addresses;
DELETE FROM terms_agreements;
DELETE FROM wishes;
DELETE FROM follows;
DELETE FROM members;

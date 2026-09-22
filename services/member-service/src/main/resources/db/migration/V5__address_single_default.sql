-- 회원당 기본 배송지는 최대 1개다. 앱 로직(AddressService)만으로는 동시 요청을 막지 못해 DB가 보장한다.
-- 인덱스를 만들기 전에 기존 중복을 정리한다 — 회원별로 가장 최근(id 최대) 기본만 남긴다.
UPDATE addresses a
SET is_default = FALSE
WHERE a.is_default
  AND a.id < (SELECT MAX(b.id) FROM addresses b WHERE b.member_id = a.member_id AND b.is_default);

CREATE UNIQUE INDEX uq_addresses_member_default ON addresses (member_id) WHERE is_default;

-- 메이커 팔로우(MEMBER-007). 행의 존재 자체가 팔로우 상태인 단순 애그리거트다.
--
-- 복합 PK (member_id, seller_id): 중복 팔로우 차단용 UNIQUE 인덱스가 따로 필요 없고,
-- 주 조회인 "내가 팔로우한 판매자 목록"이 member_id 선두라 그대로 쓰인다.
--
-- wishes.project_id와 달리 FK를 건다 — 팔로우 대상(판매자)도 결국 members의 한 행이라
-- 같은 DB 안의 참조다(타 서비스 참조가 아니다).
CREATE TABLE follows (
                         member_id  UUID NOT NULL,
                         seller_id  UUID NOT NULL,
                         created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         PRIMARY KEY (member_id, seller_id),
                         CONSTRAINT fk_follows_member FOREIGN KEY (member_id) REFERENCES members(id),
                         CONSTRAINT fk_follows_seller FOREIGN KEY (seller_id) REFERENCES members(id)
);

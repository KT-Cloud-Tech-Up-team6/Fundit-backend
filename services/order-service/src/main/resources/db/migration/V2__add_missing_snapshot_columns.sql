-- ============================================================
-- V1 스키마 이후 CLAUDE.md/API 명세 검토 과정에서 드러난 누락 컬럼 보강
-- ============================================================

-- ORDER-016: RewardUpdated를 절대값이 아니라 증분(delta)으로 반영하려면
-- "최초 생성 시 quantity" 기준값을 order-service가 별도로 보관해야 한다
-- (CLAUDE.md "핵심 설계 결정" 및 OrderDomainApiSpec.md "반영 이력" 참고).
-- 기존 행이 없으므로(V1 직후 시점) 백필 없이 NOT NULL로 추가한다.
ALTER TABLE inventories
    ADD COLUMN initial_quantity INT NOT NULL DEFAULT 0 CHECK (initial_quantity >= 0);
ALTER TABLE inventories ALTER COLUMN initial_quantity DROP DEFAULT;

-- ORDER-003/005: "리워드/옵션의 가격·이름은 주문 시점 스냅샷으로 저장"(order-service CLAUDE.md)
-- 원칙인데 V1의 funding_line_items에는 가격만 있고 이름 스냅샷 컬럼이 빠져 있었다.
ALTER TABLE funding_line_items
    ADD COLUMN reward_name VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE funding_line_items ALTER COLUMN reward_name DROP DEFAULT;

-- ORDER-004: "내 펀딩 참여 목록" 응답에 projectTitle이 필요한데, 매번 project-service를
-- 동기 조회하면 목록 API가 N+1로 느려진다. OrderDomainApiSpec.md 4번 엔드포인트의
-- [가정]을 반영해 생성 시점 스냅샷 컬럼을 추가한다(project-service가 나중에 제목을
-- 바꿔도 과거 참여 내역 표시는 생성 시점 값을 유지 — 다른 스냅샷 컬럼과 동일 원칙).
ALTER TABLE fundings
    ADD COLUMN project_title VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE fundings ALTER COLUMN project_title DROP DEFAULT;

# fulfillment-service ERD (검토·수정본)

> `CLAUDE.md`/`FulfillmentFunctionalSpec.md`/`FulfillmentApiSpec.md`가 참조하는 원본 ERD·DDL 문서. 실제 적용 스키마는
> `src/main/resources/db/migration/V1__init_schema.sql`에 이 DDL을 그대로 반영했다 — 이후 컬럼 변경은 `V1`을 수정하지
> 않고 `V2__*.sql`부터 추가한다(root `CLAUDE.md` "절대 하지 말아야 할 것").

## 검토의견 요약

1. **트래커 생성 경로**: `fulfillment_trackers`는 order-service `FundingSucceeded` 이벤트 구독(FULFILLMENT-001)으로만 생성된다. 이 배선이 끝나기 전엔 8.1(진행현황 등록) API가 전부 대상 트래커를 찾지 못해 실패하므로 최우선 구현 대상이다.
2. **`shipments` 상태 컬럼 신규**: 원안에는 `shipped_at` 이후 상태가 없었다. "배송완료"·"수령확인"이 마이페이지 상태 표시와 payment-service PAYMENT-006(하자환불 신청기간 판정)의 근거가 되므로 `status`/`delivered_at`/`receipt_confirmed_at`/`receipt_auto_confirmed`를 추가했다.
3. **`stage`/`reason_type` CHECK 보완**: `fulfillment_stage_details`/`fulfillment_schedule_changes`에 화이트리스트 CHECK가 누락돼 있어 추가했다.
4. **`shipments.project_id` FK화**: 같은 DB 안의 `fulfillment_trackers.project_id`를 참조하므로(비정규화 값이 아니라) FK로 정합성을 보장한다. 타 서비스(catalog-service/order-service) 참조는 FK를 걸지 않는다 — "같은 DB냐"가 FK 여부의 기준이다.
5. **`shipments.funding_id` UNIQUE**: 재배송·분할배송(하자 교환 등)은 이번 범위 밖으로 가정한다. 지원이 필요해지면 이 제약부터 완화해야 한다 — 정책 확인 필요.
6. **인덱스 보강**: 미갱신 트래커 배치(FULFILLMENT-004)용 부분 인덱스, 단계별 최신 상세내용 조회(`updated_at DESC` 포함), 발송 지연 판정(FULFILLMENT-008)이 반복 조회하는 `SHIPPING_OUT` 단계 `planned_end_at` 부분 인덱스를 추가했다.

## DDL

```sql
-- ============================================================
-- fulfillment-service (PRD 8장/13.2~14.3장 반영, 검토·수정본)
-- ============================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 제작~배송 진행상황은 "프로젝트당 하나"를 모든 참여자가 공유해서 본다
-- (생산 공정은 구매자마다 다르지 않음 — 배송/수령확인은 다름, shipments 참고)
-- [검토의견 1] 이 행은 order-service FundingSucceeded 이벤트 구독(FULFILLMENT-001)으로 생성된다.
-- 이벤트 구독 전에는 이 테이블이 비어 있어 8.1(진행현황 등록) API가 전부 실패하므로 반드시 먼저 구현할 것.
CREATE TABLE fulfillment_trackers (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id       BIGINT NOT NULL,             -- catalog-service 참조, FK 아님
    current_stage    VARCHAR(20) NOT NULL DEFAULT 'PRODUCTION_START'
                     CHECK (current_stage IN ('PRODUCTION_START','MANUFACTURING',
                                                'INSPECTION','SHIPPING_OUT','DELIVERY')),
    last_updated_at  TIMESTAMPTZ,   -- 판매자의 마지막 상세 진행 내용 등록 시각(1주 강제 정책 기준)
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_fulfillment_trackers_project ON fulfillment_trackers (project_id);
-- [검토의견 6, 신규] 1주 미갱신 트래커를 찾는 배치(FULFILLMENT-004)용 부분 인덱스.
-- DELIVERY 단계는 이미 전원 발송된 상태라 갱신 알림 대상에서 제외.
CREATE INDEX idx_fulfillment_trackers_stale ON fulfillment_trackers (last_updated_at)
    WHERE current_stage <> 'DELIVERY';

-- 판매자가 주기적으로 등록하는 단계별 상세 내용 — 이력 보존을 위해 append-only
CREATE TABLE fulfillment_stage_details (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tracker_id       BIGINT NOT NULL,
    stage            VARCHAR(20) NOT NULL
                     CHECK (stage IN ('PRODUCTION_START','MANUFACTURING',
                                       'INSPECTION','SHIPPING_OUT','DELIVERY')),  -- [검토의견 3, 수정] CHECK 누락 보완
    planned_start_at TIMESTAMPTZ,
    planned_end_at   TIMESTAMPTZ,
    detail_text      TEXT NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fulfillment_stage_details_tracker FOREIGN KEY (tracker_id) REFERENCES fulfillment_trackers(id)
);
-- [검토의견 6, 수정] 기존엔 (tracker_id, stage)까지만 있어 "현재 단계 최신 상세내용 1건" 조회 시
-- updated_at 정렬을 인덱스만으로 처리하지 못했음 — updated_at DESC 포함
CREATE INDEX idx_fulfillment_stage_details_tracker ON fulfillment_stage_details (tracker_id, stage, updated_at DESC);
-- [검토의견 6, 신규] 발송 지연 판정(FULFILLMENT-008, payment-service PAYMENT-008 연동)이
-- SHIPPING_OUT 단계의 planned_end_at을 반복 조회하므로 부분 인덱스 추가
CREATE INDEX idx_fulfillment_stage_details_shipping_deadline
    ON fulfillment_stage_details (tracker_id, planned_end_at) WHERE stage = 'SHIPPING_OUT';

CREATE TABLE fulfillment_schedule_changes (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tracker_id       BIGINT NOT NULL,
    stage            VARCHAR(20) NOT NULL
                     CHECK (stage IN ('PRODUCTION_START','MANUFACTURING',
                                       'INSPECTION','SHIPPING_OUT','DELIVERY')),  -- [검토의견 3, 수정] CHECK 누락 보완
    reason_type      VARCHAR(20) NOT NULL
                     CHECK (reason_type IN ('START_DELAY','STOCK_SHORTAGE',
                                              'INSPECTION_DELAY','SHIPPING_DELAY','OTHER')),
    reason_detail    TEXT,
    old_planned_date TIMESTAMPTZ,
    new_planned_date TIMESTAMPTZ,
    changed_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fulfillment_schedule_changes_tracker FOREIGN KEY (tracker_id) REFERENCES fulfillment_trackers(id)
);
CREATE INDEX idx_fulfillment_schedule_changes_tracker ON fulfillment_schedule_changes (tracker_id);

-- 발송정보(운송장)는 참여자마다 배송지가 달라 개별 funding 단위로 관리.
-- [검토의견 2, 신규] status/delivered_at/receipt_confirmed_at을 추가 — "배송완료"·"수령확인"은
-- 마이페이지(FL_B_MY_01_01) 상태 표시와 payment-service PAYMENT-006(하자환불, "수령 후 기간 내")
-- 신청기간 판정의 근거가 되는데, 기존 스키마엔 shipped_at 이후 상태가 전혀 없었음.
-- delivered_at은 택배사 API 미연동(요구사항정의서 8.3.3) 상황에서 배치(FULFILLMENT-007)가
-- shipped_at 기준 N일 경과 시 목업으로 채운다 — N일 값은 정책값, 협의 필요.
CREATE TABLE shipments (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    funding_id             BIGINT NOT NULL,              -- order-service 참조, FK 아님
    project_id             BIGINT NOT NULL,              -- [검토의견 4] fulfillment_trackers(project_id) FK로 정합성 보장(같은 DB)
    status                 VARCHAR(20) NOT NULL DEFAULT 'PREPARING'
                           CHECK (status IN ('PREPARING','SHIPPED','DELIVERED','RECEIPT_CONFIRMED')),  -- [검토의견 2, 신규]
    carrier                VARCHAR(50),
    tracking_number        VARCHAR(100),
    shipped_at             TIMESTAMPTZ,
    delivered_at           TIMESTAMPTZ,                  -- [검토의견 2, 신규] "배송완료" 처리 시각(목업 배치 또는 향후 택배사 웹훅)
    receipt_confirmed_at   TIMESTAMPTZ,                  -- [검토의견 2, 신규] 구매자 "수령 확인" 처리 시각
    receipt_auto_confirmed BOOLEAN NOT NULL DEFAULT FALSE, -- [검토의견 2, 신규] 미확인으로 시스템이 자동 확정했는지 구분(FULFILLMENT-010)
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- [검토의견 2, 신규] status 전이 이력 추적용
    CONSTRAINT fk_shipments_tracker_project FOREIGN KEY (project_id)
        REFERENCES fulfillment_trackers (project_id)  -- [검토의견 4, 신규] 같은 DB이므로 FK로 정합성 보장(기존엔 비정규화 값으로만 저장)
);
CREATE UNIQUE INDEX uq_shipments_funding ON shipments (funding_id);  -- [검토의견 5, 수정] 비고유 INDEX → UNIQUE (재배송/분할배송은 범위 밖으로 가정, 정책 확인 필요)
CREATE TRIGGER trg_shipments_updated_at
    BEFORE UPDATE ON shipments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

# fulfillment-service API 명세서

> `fullfillmentFunctionalSpec.md`(FULFILLMENT-001~010)를 기준으로 작성했습니다. 공통 규칙(버저닝·응답 포맷·HTTP 상태 코드·공통 에러 코드)은 `API 버저닝, 표준 에러 형식 정의서`를 따릅니다.
>
> **재검토 변경사항(2차)**: `fullfillmentFunctionalSpec.md` 2차 검토에서 발견된 두 가지 수정사항을 반영했습니다 — (1) FULFILLMENT-006/#5의 "전체 funding 발송 완료 시 DELIVERY 자동 전이" 로직 제거(판정 불가능한 로직이었음), (2) FULFILLMENT-008/#8이 미발송 funding의 `projectId`를 order-service 내부 API로 조회하도록 수정. 겸사겸사 #5·6·7 경로에 `projectId`를 추가해 소유권 검증을 단순화했습니다.

## 엔드포인트 목록

| # | Method | Path | 설명 | 인증 | 관련 기능 ID |
| --- | --- | --- | --- | --- | --- |
| 1 | GET | `/api/v1/projects/{projectId}/fulfillment` | 제작·배송 진행 현황 조회 | X (공통) | FULFILLMENT-003 |
| 2 | PATCH | `/api/v1/projects/{projectId}/fulfillment/stage` | 단계 전환 | O (판매자) | FULFILLMENT-002 |
| 3 | POST | `/api/v1/projects/{projectId}/fulfillment/stage-details` | 단계별 예상일정·상세 진행 내용 등록 | O (판매자) | FULFILLMENT-002 |
| 4 | POST | `/api/v1/projects/{projectId}/fulfillment/schedule-changes` | 일정 변경·지연사유 등록 | O (판매자) | FULFILLMENT-005 |
| 5 | POST | `/api/v1/projects/{projectId}/fundings/{fundingId}/shipment` | 발송정보 등록(발송 처리) | O (판매자) | FULFILLMENT-006 |
| 6 | GET | `/api/v1/projects/{projectId}/fundings/{fundingId}/shipment` | 배송현황(발송정보) 조회 | O (구매자) | FULFILLMENT-003 |
| 7 | POST | `/api/v1/projects/{projectId}/fundings/{fundingId}/shipment/confirm-receipt` | 수령 확인 처리 | O (구매자) | FULFILLMENT-009 |
| 8 | GET | `/internal/fundings/{fundingId}/fulfillment-status` | 배송 상태 내부 조회(payment-service 연동) | 내부(게이트웨이 시크릿) | FULFILLMENT-008 |

> FULFILLMENT-001(트래커 초기화), FULFILLMENT-004(미등록 알림), FULFILLMENT-007(배송완료 목업 처리), FULFILLMENT-010(미확인 자동확정)은 이벤트/스케줄러로만 트리거되어 REST 엔드포인트가 없습니다. 하단 "이벤트 발행/구독" 섹션에 정리했습니다.
>
> **(2차 검토)** #5·6·7은 초안에서 `/api/v1/fundings/{fundingId}/...`였으나 `projectId`를 경로에 포함하도록 수정했습니다. projectId가 경로에 있으면 판매자 소유권 검증(#5)이 "이 funding이 내 프로젝트 소속인가"라는 order-service 교차조회 없이 "이 프로젝트가 내 것인가"만으로 가능해지고, #7의 자동 DELIVERY 전이 로직 제거와 함께 일관성이 맞습니다.

---

## 상세 명세

### 1. 제작·배송 진행 현황 조회

```
GET /api/v1/projects/{projectId}/fulfillment
```

**Auth Required**: X (공통)

**Request**: Path Parameter: `projectId`

**Response Body**

```json
{
  "projectId": 123,
  "currentStage": "SHIPPING_OUT",
  "lastUpdatedAt": "2026-09-08T10:00:00",
  "isUpdateOverdue": false,
  "stages": [
    { "stage": "PRODUCTION_START", "status": "COMPLETED",
      "plannedStartAt": "2026-08-20T00:00:00", "plannedEndAt": "2026-08-25T00:00:00",
      "detailText": "도면 확정 및 협력업체 발주 완료", "updatedAt": "2026-08-24T09:00:00" },
    { "stage": "MANUFACTURING", "status": "COMPLETED", "detailText": "..." },
    { "stage": "INSPECTION", "status": "COMPLETED", "detailText": "..." },
    { "stage": "SHIPPING_OUT", "status": "IN_PROGRESS",
      "plannedStartAt": "2026-09-05T00:00:00", "plannedEndAt": "2026-09-10T00:00:00",
      "detailText": "포장 완료, 순차 출고 중", "updatedAt": "2026-09-08T10:00:00" },
    { "stage": "DELIVERY", "status": "NOT_STARTED" }
  ],
  "scheduleChanges": [
    { "stage": "SHIPPING_OUT", "reasonType": "STOCK_SHORTAGE", "reasonDetail": "부자재 입고 지연",
      "oldPlannedDate": "2026-09-05T00:00:00", "newPlannedDate": "2026-09-10T00:00:00",
      "changedAt": "2026-09-01T11:00:00" }
  ]
}
```

**Validation / Business Rules**

- `stages[].status`는 `fulfillment_trackers.current_stage`와 5단계 순서를 비교해 계산(`COMPLETED`/`IN_PROGRESS`/`NOT_STARTED`). 각 단계의 `detailText`/`updatedAt`은 해당 단계의 최신 `fulfillment_stage_details` 1건.
- `isUpdateOverdue`는 `current_stage <> 'DELIVERY'`이고 `lastUpdatedAt`이 7일[정책값] 이상 경과했을 때 `true` — 프론트가 "업데이트 예정" 문구를 노출하는 근거(14.3.3).
- 트래커가 없는 프로젝트(아직 미성립) 조회 시 `404 NOT_FOUND`.

---

### 2. 단계 전환

```
PATCH /api/v1/projects/{projectId}/fulfillment/stage
```

**Auth Required**: O (판매자)

**Request**

```json
{ "stage": "SHIPPING_OUT" }
```

**Response Body**

```json
{ "projectId": 123, "currentStage": "SHIPPING_OUT" }
```

**Validation / Business Rules**

- 소유권 검증(S4): 본인 소유 프로젝트가 아니면 `403 FORBIDDEN`.
- 5단계 CHECK 값 외 요청 → `400 INVALID_INPUT`.
- 역방향 전이(현재보다 앞 단계로) 시도 → `422 BUSINESS_RULE_VIOLATION`("이미 지난 단계입니다").

---

### 3. 단계별 예상일정·상세 진행 내용 등록

```
POST /api/v1/projects/{projectId}/fulfillment/stage-details
```

**Auth Required**: O (판매자)

**Request**

```json
{
  "stage": "SHIPPING_OUT",
  "plannedStartAt": "2026-09-05T00:00:00",
  "plannedEndAt": "2026-09-10T00:00:00",
  "detailText": "포장 완료, 순차 출고 중"
}
```

**Response Body**

```json
{ "stageDetailId": 501, "stage": "SHIPPING_OUT", "detailText": "포장 완료, 순차 출고 중", "updatedAt": "2026-09-08T10:00:00" }
```

**Validation / Business Rules**

- 소유권 검증(S4). `detailText` 출력 시 인코딩 적용(S2, 구매자 화면 노출).
- 이력 보존을 위해 항상 새 행을 append(수정이 아니라 등록) — `fulfillment_trackers.last_updated_at`을 이 시각으로 갱신해 1주 강제 정책(FULFILLMENT-004)의 기준을 리셋.
- `detailText` 누락 → `400 INVALID_INPUT`.

---

### 4. 일정 변경·지연사유 등록

```
POST /api/v1/projects/{projectId}/fulfillment/schedule-changes
```

**Auth Required**: O (판매자)

**Request**

```json
{
  "stage": "SHIPPING_OUT",
  "reasonType": "STOCK_SHORTAGE",
  "reasonDetail": "부자재 입고 지연",
  "newPlannedDate": "2026-09-10T00:00:00"
}
```

**Response Body**

```json
{
  "scheduleChangeId": 88, "stage": "SHIPPING_OUT", "reasonType": "STOCK_SHORTAGE",
  "oldPlannedDate": "2026-09-05T00:00:00", "newPlannedDate": "2026-09-10T00:00:00",
  "changedAt": "2026-09-01T11:00:00"
}
```

**Validation / Business Rules**

- `reasonType`은 `START_DELAY`\|`STOCK_SHORTAGE`\|`INSPECTION_DELAY`\|`SHIPPING_DELAY`\|`OTHER` 화이트리스트 검증(400). `OTHER`이면 `reasonDetail` 필수.
- 저장 시 해당 단계의 기존 예상일정을 `oldPlannedDate`로 스냅샷하고, `fulfillment_stage_details`의 예상일정도 함께 갱신.
- 저장과 동시에 구매자 알림 이벤트 발행(notification-service 구독, 이 문서 범위 밖).
- 소유권 검증(S4), `reasonDetail` 출력 인코딩(S2).

---

### 5. 발송정보 등록(발송 처리)

```
POST /api/v1/projects/{projectId}/fundings/{fundingId}/shipment
```

**Auth Required**: O (판매자)

**Request**: Path Parameter: `projectId`, `fundingId`

```json
{ "carrier": "CJ대한통운", "trackingNumber": "123456789012" }
```

**Response Body**

```json
{ "fundingId": 1024, "status": "SHIPPED", "carrier": "CJ대한통운", "trackingNumber": "123456789012", "shippedAt": "2026-09-08T14:00:00" }
```

**Validation / Business Rules**

- 소유권 검증: 본인 소유 프로젝트(path의 `projectId`)인지 확인(S4). `fundingId`가 실제로 그 프로젝트 소속인지는 order-service 내부 API로 교차 검증[가정].
- 이미 `SHIPPED` 이상 상태인 건 재등록 시도 → `409 CONFLICT`.
- `carrier`·`trackingNumber` 누락 → `400 INVALID_INPUT`.
- 요구사항정의서 8.3.3에 따라 실제 택배사 배송추적 API 연동은 하지 않음(목업) — 이후 배송완료 전환은 FULFILLMENT-007 배치가 처리.
- **(2차 검토)** 초안에는 "프로젝트의 모든 funding이 SHIPPED 이상이면 DELIVERY로 자동 전이"하는 규칙이 있었으나 제거함(전체 funding 개수를 알 수 없어 판정 불가능) — DELIVERY로의 전이는 엔드포인트 #2(단계 전환)를 통해 판매자가 직접 수행.

---

### 6. 배송현황(발송정보) 조회

```
GET /api/v1/projects/{projectId}/fundings/{fundingId}/shipment
```

**Auth Required**: O (구매자)

**Request**: Path Parameter: `projectId`, `fundingId`

**Response Body**

```json
{
  "fundingId": 1024, "status": "DELIVERED",
  "carrier": "CJ대한통운", "trackingNumber": "123456789012",
  "shippedAt": "2026-09-08T14:00:00", "deliveredAt": "2026-09-11T09:00:00",
  "receiptConfirmedAt": null, "canConfirmReceipt": true
}
```

**Validation / Business Rules**

- 소유권 검증(S4): 본인 funding이 아니면 `403 FORBIDDEN`.
- 아직 발송 전(`shipments` 레코드 없음)이면 `status: "PREPARING"`, 나머지 필드는 `null`로 응답(에러 아님).
- `canConfirmReceipt`는 `status='DELIVERED'`일 때만 `true` — 프론트가 "수령확인" 버튼 노출 여부를 결정.

---

### 7. 수령 확인 처리

```
POST /api/v1/projects/{projectId}/fundings/{fundingId}/shipment/confirm-receipt
```

**Auth Required**: O (구매자)

**Request**: Path Parameter: `projectId`, `fundingId` / Body 없음

**Response Body**

```json
{ "fundingId": 1024, "status": "RECEIPT_CONFIRMED", "receiptConfirmedAt": "2026-09-11T15:00:00" }
```

**Validation / Business Rules**

- `status='DELIVERED'`가 아닌 상태(아직 미발송·미배송완료)에서 시도 → `422 BUSINESS_RULE_VIOLATION`.
- 이미 `RECEIPT_CONFIRMED`인 건에 재요청 → 에러 아님, 동일 응답 재반환(idempotent).
- 본인 funding만 확인 가능(S4).
- 이 시각이 payment-service PAYMENT-006(하자환불) 신청기간의 기산일이 됨.

---

### 8. 배송 상태 내부 조회(payment-service 연동)

```
GET /internal/fundings/{fundingId}/fulfillment-status
```

**Auth Required**: 내부 전용(게이트웨이 우회 차단 — `InternalGatewaySecretFilter`와 동일한 내부 API 키 검증)

**Request**: Path Parameter: `fundingId`

**Response Body**

```json
{ "isAlreadyShipped": true, "isDelayed": false, "deliveredAt": "2026-09-11T09:00:00", "receiptConfirmedAt": null }
```

**Validation / Business Rules**

- `shipments` 레코드가 있으면 그 값을 그대로 사용해 `isAlreadyShipped`(`status IN ('SHIPPED','DELIVERED','RECEIPT_CONFIRMED')`), `deliveredAt`, `receiptConfirmedAt`을 채움.
- `shipments` 레코드가 없으면(아직 발송 전) order-service 내부 API `GET /internal/fundings/{fundingId}`(payment-service `OrderFundingClient`가 이미 호출 중인 것과 동일 엔드포인트)를 호출해 `projectId`를 조회한 뒤, 해당 프로젝트의 `SHIPPING_OUT` 단계 최신 `fulfillment_stage_details.planned_end_at`과 현재 시각을 비교해 `isDelayed`를 계산. 이 경우 `isAlreadyShipped: false`, `deliveredAt`/`receiptConfirmedAt`은 `null`.
- payment-service `ShippingStatusClient`(PAYMENT-008)가 `isAlreadyShipped`/`isDelayed`를, PAYMENT-006(하자환불 신청기간 판정)이 `receiptConfirmedAt`을 사용.
- order-service 내부 API 호출 실패/타임아웃 → `503 DEPENDENCY_FAILURE`(payment-service 쪽에서 재시도 또는 판정 보류로 처리).
- **(2차 검토)** 초안은 "`shipments` 없으면 그냥 미발송으로 응답"하고 끝내려 했으나, 발송 전에는 `projectId`를 들고 있는 유일한 테이블(`shipments`)에 행이 없어 `isDelayed`를 계산할 방법 자체가 없었다 — PAYMENT-008이 실제로 궁금해하는 케이스(미발송+지연 여부)를 판정 못 하는 설계였다. order-service 내부 API로 `projectId`를 조회하도록 수정.

---

## 이벤트 발행/구독

REST로 노출되지 않는 이벤트/스케줄러 기반 기능(FULFILLMENT-001, 004, 007, 010)은 아래와 같이 연결됩니다.

| 기능 ID | 트리거 | 내용 |
| --- | --- | --- |
| FULFILLMENT-001 | 이벤트 구독: `FundingSucceeded`(order-service ORDER-006 발행) | 프로젝트 성립 시 `fulfillment_trackers` 생성(idempotent) |
| FULFILLMENT-004 | 스케줄러 | `current_stage <> 'DELIVERY'`이고 `last_updated_at` 7일[정책값] 경과한 트래커 대상 판매자 알림 이벤트 발행 |
| FULFILLMENT-007 | 스케줄러 | `status='SHIPPED'`이고 `shipped_at` 3영업일[정책값] 경과한 건을 `DELIVERED`로 전이(택배사 미연동 목업) |
| FULFILLMENT-010 | 스케줄러 | `status='DELIVERED'`이고 `delivered_at` 7일[정책값] 경과한 건을 `RECEIPT_CONFIRMED`(자동확정)로 전이, 구매자 안내 알림 발행 |

> 실제 택배사 배송추적 API가 연동되면 FULFILLMENT-007은 스케줄러 대신 웹훅 수신 로직으로 대체될 자리입니다(payment-service의 토스 웹훅과 동일한 패턴).

---

## 에러 코드 매핑

`error-handling.md` 기준으로, 공통 코드는 `CommonErrorCode`를 그대로 쓰고 새 코드만 `FulfillmentErrorCode`(도메인 전용, `implements ErrorCode`)에 추가합니다.

| 상황 | 코드 | HTTP | 관련 항목 |
| --- | --- | --- | --- |
| 트래커 없음(미성립 프로젝트 등) | `CommonErrorCode.NOT_FOUND`(기존) | 404 | FULFILLMENT-002, 003 |
| 이미 발송 처리된 건 재등록 시도 | `FulfillmentErrorCode.ALREADY_SHIPPED`(신규) | 409 | FULFILLMENT-006 |
| 배송완료 전 수령확인 시도 | `FulfillmentErrorCode.NOT_YET_DELIVERED`(신규) | 422 | FULFILLMENT-009 |
| 단계 역방향 전이 시도 | `FulfillmentErrorCode.INVALID_STAGE_TRANSITION`(신규) | 422 | FULFILLMENT-002 |
| `reasonType` 화이트리스트 밖 값 / `OTHER`인데 상세사유 누락 | `CommonErrorCode.INVALID_INPUT`(기존) | 400 | FULFILLMENT-005 |
| 타 판매자 소유 프로젝트/펀딩 접근 | `CommonErrorCode.FORBIDDEN`(기존) | 403 | FULFILLMENT-002, 005, 006 |
| 타인 funding 조회/수령확인 시도 | `CommonErrorCode.FORBIDDEN`(기존) | 403 | FULFILLMENT-003, 009 |
| order-service(funding-project 소유관계, projectId 조회 등) 호출 실패 | `CommonErrorCode.DEPENDENCY_FAILURE`(기존) | 503 | FULFILLMENT-002, 006, 008, 009 |
| 필수값 누락(택배사·운송장번호·상세내용 등) | `CommonErrorCode.INVALID_INPUT`(기존) | 400 | FULFILLMENT-002, 006 |
| 수령확인 중복 요청 | 에러 아님 — idempotent 200 | - | FULFILLMENT-009 |
## 엔드포인트 목록

| # | Method | Path | 설명 | 인증 | 관련 기능 ID |
| --- | --- | --- | --- | --- | --- |
| 1 | GET | `/api/v1/projects/{projectId}/supporters` | 서포터 활동 목록 조회 | X (공통) | ORDER-001 |
| 2 | POST | `/api/v1/orders/preview` | 결제금액 계산(미리보기, 쿠폰 적용/해제 포함) | O (구매자) | ORDER-002, ORDER-010 |
| 3 | POST | `/api/v1/orders` | 펀딩 주문 생성(재고 검증/차감) | O (구매자) | ORDER-003 |
| 4 | GET | `/api/v1/orders` | 내 펀딩 참여 목록 조회 | O (구매자) | ORDER-004 |
| 5 | GET | `/api/v1/orders/{orderId}` | 개별 펀딩 참여 상세 조회 | O (구매자) | ORDER-005 |
| 6 | POST | `/api/v1/orders/{orderId}/cancel` | 참여 취소(단순변심) | O (구매자) | ORDER-014 |
| 7 | POST | `/api/v1/reward-restock-notifications` | 재입고(품절) 알림 신청 | O (구매자) | ORDER-011 |
| 8 | POST | `/api/v1/sellers/coupons` | 쿠폰 발급(메이커) | O (판매자) | ORDER-008 |
| 9 | POST | `/api/v1/coupons/{couponCode}/claim` | 쿠폰 발급받기(소비자 능동 클레임) | O (구매자) | ORDER-012 |
| 10 | GET | `/api/v1/coupons/me` | 쿠폰함 조회 | O (구매자) | ORDER-009 |

> ORDER-006(펀딩 마감 목표달성 판정), ORDER-007(쿠폰 발급-플랫폼 자동), ORDER-013(미결제 주문 자동 만료), ORDER-015(쿠폰 사용처리·복원), ORDER-016(리워드 이벤트 구독-재고 동기화)은 스케줄러/이벤트로만 트리거되어 REST 엔드포인트가 없습니다. 하단 "이벤트 발행/구독" 섹션에 정리했습니다.

---

## 상세 명세

### 1. 서포터 활동 목록 조회

```
GET /api/v1/projects/{projectId}/supporters
```

**Auth Required**: X (공통)

**Request**: Path Parameter: `projectId` / Query Parameter: `page`, `size`(선택, 기본 0/20)

**Response Body**

```json
{
  "content": [
    { "displayName": "구매***", "amount": 39000, "relativeTime": "3분 전" },
    { "displayName": "익명", "amount": null, "relativeTime": "10분 전" }
  ],
  "page": 0, "size": 20, "totalElements": 128, "totalPages": 7, "hasNext": true
}
```

**Validation / Business Rules**

- 최신순 정렬, 무한스크롤(페이지네이션).
- 공개설정에 동의하지 않은 참여자는 `displayName`을 "익명" 등으로, `amount`를 `null`로 마스킹해 개인정보 노출을 방지(S9). 공개설정 값은 member-service가 소유 — 주문 조회 시 member-service를 동기 호출하거나 `fundings` 생성 시점에 스냅샷으로 받아두는 방식 중 선택 필요[정책 확인 필요].
- 참여자가 없는 프로젝트는 `content: []`로 반환(Empty State는 프론트 처리).

---

### 2. 결제금액 계산(미리보기, 쿠폰 적용/해제 포함)

```
POST /api/v1/orders/preview
```

**Auth Required**: O (구매자)

**Request**

```json
{
  "projectId": 123,
  "lineItems": [
    { "rewardId": 1, "quantity": 1, "optionValueIds": [100] }
  ],
  "shippingAddress": {
    "recipientName": "홍길동", "phoneNumber": "010-1234-5678",
    "zipcode": "12345", "addressLine1": "서울시 ...", "addressLine2": "101동 101호"
  },
  "couponCodes": ["WELCOME2026", "PJT123-A1B2"]
}
```

**Response Body**

```json
{
  "rewardAmount": 39000,
  "shippingFee": 3000,
  "discountAmount": 6900,
  "finalAmount": 35100,
  "appliedCoupons": [
    { "couponCode": "WELCOME2026", "issuerType": "PLATFORM", "discountType": "FREE_SHIPPING" },
    { "couponCode": "PJT123-A1B2", "issuerType": "MAKER", "discountType": "RATE" }
  ],
  "unavailableCoupons": []
}
```

**Validation / Business Rules**

- **비영속(stateless) 계산 전용 엔드포인트.** 호출해도 DB에 아무것도 생성되지 않는다 — ORDER-003(주문 생성) 전까지 사용자가 리워드/배송지/쿠폰을 바꿀 때마다 반복 호출해 최종 금액을 미리 확인하는 용도(PRD 13.2).
- `couponCodes`를 생략하면 할인 없이 계산(ORDER-002 단독 동작), 포함하면 해당 쿠폰들을 적용해 재계산(ORDER-010 동작을 겸함).
- `couponCodes`는 **최대 2개, 그것도 `issuer_type`이 서로 달라야 함**(플랫폼쿠폰 1개 + 메이커쿠폰 1개까지만 — 16.3.3). 같은 issuer_type을 2개 보내면 `400 INVALID_INPUT`, 3개 이상 보내면 `400 INVALID_INPUT`.
- 최대 할인 쿠폰을 자동 추천하려면 `couponCodes` 없이 호출한 뒤 `unavailableCoupons`/추천 쿠폰 목록을 함께 내려주는 방식으로 확장 가능[가정: 이번 버전은 명시적 `couponCodes` 지정만 지원].
- 금액 계산은 항상 서버에서 수행(리워드 단가·배송비·쿠폰 할인율 모두 서버 조회값 사용), 클라이언트가 보낸 금액을 신뢰하지 않는다(S4).
- 리워드금액 합산 + 배송비(프로젝트/리워드 정책값) + 쿠폰 할인(`FREE_SHIPPING`은 배송비 한도 내에서 할인, `coupons.max_discount_amount` 설정 시 그 한도까지) 반영.
- 쿠폰이 최소 펀딩금액 미달·만료·본인 미보유 등으로 적용 불가능하면 `discountAmount=0`, `appliedCoupon=null`로 응답하고 사유는 `unavailableCoupons`에 코드로 안내(예: `MIN_AMOUNT_NOT_MET`, `EXPIRED`, `NOT_OWNED`).
- **[설계 가정]** 기존 기능명세서 ORDER-010의 입력값이 "fundingId(임시 주문서)"로 되어 있었으나, ORDER-002(주문서 작성)와 ORDER-010(쿠폰 적용)이 동일 화면(13.2)의 한 흐름이라 이 문서에서는 두 기능을 하나의 stateless 미리보기 엔드포인트로 합쳤습니다. 만약 실제로 "쿠폰 적용 전에 먼저 주문(Funding) 레코드가 만들어져 있어야 한다"는 의도였다면 설계가 달라져야 하니 확인 부탁드립니다.

---

### 3. 펀딩 주문 생성(재고 검증/차감)

```
POST /api/v1/orders
```

**Auth Required**: O (구매자)

**Request**: 미리보기(`/orders/preview`)와 동일한 바디(리워드/옵션/수량, 배송지, `couponCodes`(선택, 최대 2개 — 위 2번 엔드포인트 규칙과 동일))

**Response Body**

```json
{
  "orderId": "018f9a1b-....-....-............",
  "status": "PENDING",
  "finalAmount": 39000,
  "paymentExpiresAt": "2026-09-07T10:30:00"
}
```

**Validation / Business Rules**

- 하나의 트랜잭션으로 `inventories`를 대상으로 재고 검증 및 조건부 UPDATE(낙관적 락, `version` 컬럼)로 차감 → `fundings`(status=`PENDING`, `payment_expires_at` = 생성시각 + N분[정책값, 협의 필요]) + `funding_line_items` (+ 옵션 스냅샷 `funding_line_item_options`) 생성.
- 재고 부족 시 `409 CONFLICT`, 트랜잭션 롤백(재고 변경 없음) — 응답에 부족한 `rewardId` 포함.
- 쿠폰 적용 시 `funding_coupon_applications` 생성 및 `coupon_issuances.status`는 아직 변경하지 않음(사용확정 처리는 ORDER-015가 결제완료 이벤트로 수행).
- 금액은 서버에서 재검증(클라이언트 전달값 불신, S4), 재고 차감 쿼리는 바인딩 변수 사용(S1).
- 응답의 `orderId`는 `fundings.public_id`(UUID). 이후 결제 요청은 payment-service의 `POST /api/v1/payments`(orderId 전달)로 이어진다(이 문서 범위 밖).

---

### 4. 내 펀딩 참여 목록 조회

```
GET /api/v1/orders
```

**Auth Required**: O (구매자)

**Request**: Query Parameter: `status`(선택), `page`, `size`(선택, 기본 0/20)

**Response Body**

```json
{
  "content": [
    {
      "orderId": "018f9a1b-....",
      "projectId": 123, "projectTitle": "세상에 없는 프라이팬",
      "status": "FUNDING_IN_PROGRESS", "finalAmount": 39000,
      "createdAt": "2026-09-07T10:00:00"
    }
  ],
  "page": 0, "size": 20, "totalElements": 3, "totalPages": 1, "hasNext": false
}
```

**Validation / Business Rules**

- `member_id` 기준 본인 참여 내역만 조회(S4).
- `status` 파라미터 미지정 시 전체 상태 반환(펀딩중/미달/제작착수~배송완료), 배송 진행 단계는 shipping-service 조회가 필요하므로 이 문서 범위 밖 — 화면에서는 이 목록 응답에 없는 배송 세부 상태를 별도 API로 조합해야 함[가정].
- `projectTitle`은 project-service 조회 없이 즉시 응답하기 위해 `fundings` 생성 시점 스냅샷으로 저장해두는 방안을 권장[가정: `fundings`에 `project_title` 스냅샷 컬럼 추가 필요 — 현재 ERD에는 없음, project-service 실시간 조회로 대체 가능하나 응답 지연 발생].

---

### 5. 개별 펀딩 참여 상세 조회

```
GET /api/v1/orders/{orderId}
```

**Auth Required**: O (구매자)

**Request**: Path Parameter: `orderId`(public_id)

**Response Body**

```json
{
  "orderId": "018f9a1b-....",
  "status": "FUNDING_IN_PROGRESS",
  "lineItems": [
    { "rewardId": 1, "rewardName": "얼리버드 패키지", "quantity": 1, "unitPrice": 39000,
      "options": [ { "optionGroupName": "색상", "optionValue": "화이트" } ] }
  ],
  "shippingFee": 3000, "finalAmount": 39000,
  "shippingAddress": { "recipientName": "홍길동", "addressLine1": "..." },
  "paidAt": "2026-09-07T10:05:00",
  "availableActions": ["CANCEL"]
}
```

**Validation / Business Rules**

- `orderId`(public_id) 소유권 서버 검증 — 타인 주문 접근 시 `403 FORBIDDEN`(S4).
- `availableActions`는 `status`에 따라 계산: `PENDING`/`FUNDING_IN_PROGRESS` → `["CANCEL"]`, `GOAL_FAILED_REFUNDED` → `[]`(자동 환불 진행, 환불내역 화면으로 안내), `GOAL_ACHIEVED`(발송 전) → `["SHIPPING_DELAY_REFUND_REQUEST"]`, 배송 완료 후 → `["DEFECT_REFUND_REQUEST"]` — 발송지연/하자환불 신청 자체는 payment-service 엔드포인트로 연결(이 문서 범위 밖).
- 불가능한 액션 시도 시(예: 마감 후 취소) → `422 BUSINESS_RULE_VIOLATION`.

---

### 6. 참여 취소(단순변심)

```
POST /api/v1/orders/{orderId}/cancel
```

**Auth Required**: O (구매자)

**Request**: Path Parameter: `orderId` / Body 없음

**Response Body**

```json
{ "orderId": "018f9a1b-....", "status": "CANCELLED_BY_MEMBER" }
```

**Validation / Business Rules**

- `fundings.status`가 `PENDING` 또는 `FUNDING_IN_PROGRESS`(=펀딩 진행중, 마감 전)인지 검증 → `status='PAYMENT_EXPIRED'`이면 `410 RESOURCE_EXPIRED`("이미 만료된 주문입니다"), 그 외(성립/미달 판정 이후 상태)는 `422 BUSINESS_RULE_VIOLATION`("펀딩이 종료되어 취소할 수 없습니다").
- 검증 통과 시 `status=CANCELLED_BY_MEMBER`로 전이하고 차감했던 재고를 원복, `FundingCancelledByMember` 이벤트 발행(payment-service가 구독해 실제 결제취소·환불 실행, ORDER-014/PAYMENT-004 참고).
- 실제 환불 완료 여부는 이 응답에 포함하지 않음(비동기) — 환불 상태는 payment-service의 환불 내역 조회 API로 확인.
- 본인 주문만 취소 가능, `orderId` 조작으로 타인 주문 취소 차단(S4).

---

### 7. 재입고(품절) 알림 신청

```
POST /api/v1/reward-restock-notifications
```

**Auth Required**: O (구매자)

**Request**: { "rewardId": 1 }

**Response Body**

```json
{ "rewardId": 1, "requested": true }
```

**Validation / Business Rules**

- `(reward_id, member_id)` upsert — 이미 신청한 리워드 재신청도 에러 없이 동일 응답(idempotent, 찜/팔로우와 동일 원칙).
- 본인 계정 기준으로만 신청(S4).
- 재입고/재오픈 시 notification-service가 이 신청 내역을 구독해 알림 발송(이 문서 범위 밖).
- **⚠️ 확인 필요**: member-service의 `MvpImplementationSummary.md`에도 동일 기능(MEMBER-008, `POST /api/v1/reward-alerts`, 별도의 `reward_alerts` 테이블 신설안)이 후순위 항목으로 남아 있습니다. 두 서비스가 같은 기능을 서로 다른 테이블로 중복 설계하고 있는 상태이니, order-service의 `reward_restock_notify_requests`로 소유권을 확정하고 member-service `MvpImplementationSummary.md`의 MEMBER-008 항목은 제거하는 것을 권장합니다(재고 정보와 강하게 결합된 기능이라 order-service 소유가 자연스러움).

---

### 8. 쿠폰 발급(메이커)

```
POST /api/v1/sellers/coupons
```

**Auth Required**: O (판매자)

**Request**

```json
{
  "projectId": 123,
  "couponName": "오픈 기념 10% 할인",
  "discountType": "RATE",
  "discountValue": 10,
  "maxDiscountAmount": 5000,
  "budgetLimit": 1000000,
  "quantity": 200,
  "minFundingAmount": 30000,
  "perMemberLimit": 1,
  "expiresAt": "2026-09-30T23:59:59"
}
```

**Response Body**

```json
{
  "couponCode": "PJT123-A1B2",
  "couponName": "오픈 기념 10% 할인",
  "remainingQuantity": 200,
  "budgetLimit": 1000000,
  "usedBudgetAmount": 0
}
```

**Validation / Business Rules**

- 본인 소유 프로젝트에만 발급 가능 — `projectId`의 `seller_id`를 project-service 조회(또는 스냅샷)로 검증(S4).
- `discountType='FREE_SHIPPING'`이면 `discountValue=0`으로 저장, 적용 시점에 해당 주문의 `shippingFee`가 할인액이 됨.
- `budgetLimit` 초과 시(`used_budget_amount + 예상차감액 > budget_limit`) 발급 자체는 성공하되 이후 적용 단계(주문 preview)에서 소진 시 자동으로 노출 제외.
- `couponCode`는 서버에서 생성(예: `{프로젝트코드}-{랜덤4자리}`), 클라이언트가 지정하지 않음.
- 발급주체=메이커이므로 할인분은 이 메이커 정산에서 차감됨(`PaymentDomainFunctionalSpec.md`의 PAYMENT-012 "쿠폰 정산 차감" 연계 — 별도 안내 문구 없음, 판매자는 정산 화면에서 확인).

---

### 9. 쿠폰 발급받기(소비자 능동 클레임)

```
POST /api/v1/coupons/{couponCode}/claim
```

**Auth Required**: O (구매자)

**Request**: Path Parameter: `couponCode` / Body 없음

**Response Body**

```json
{ "couponCode": "LIVE-XY12", "issued": true, "expiresAt": "2026-09-07T21:00:00" }
```

**Validation / Business Rules**

- `coupons.remaining_quantity`를 `version` 낙관적 락으로 조건부 차감(`UPDATE ... WHERE remaining_quantity > 0 AND version = :v`) 후 `coupon_issuances` 생성 — 갱신 실패(동시 소진) 시 짧게 재시도, 최종 실패 시 `409 CONFLICT`("쿠폰이 모두 소진되었습니다").
- `per_member_limit` 초과 발급 시도 → `422 BUSINESS_RULE_VIOLATION`.
- `issue_channel='LIVE'`인 쿠폰은 `live_session_id`가 현재 진행 중(또는 정책상 허용된 유예시간 내)인지 검증 — live-service 조회 또는 이벤트 기반 캐시[가정: 이번 버전은 live-service 동기 조회].
- 일반(`issue_channel='GENERAL'`) 쿠폰에도 동일 엔드포인트를 재사용할 수 있음 — 다만 요구사항정의서상 명시적으로 "받기" 액션이 정의된 것은 라이브 쿠폰(16.6)뿐이라, 일반 쿠폰까지 이 엔드포인트로 받는 것이 맞는지는 [정책 확인 필요].

---

### 10. 쿠폰함 조회

```
GET /api/v1/coupons/me
```

**Auth Required**: O (구매자)

**Request**: Query Parameter: `status`(선택, `AVAILABLE`\|`USED`\|`EXPIRED`)

**Response Body**

```json
{
  "content": [
    {
      "couponCode": "WELCOME2026", "couponName": "신규가입 축하 쿠폰",
      "discountType": "AMOUNT", "discountValue": 3000,
      "status": "AVAILABLE", "expiresAt": "2026-12-31T23:59:59"
    }
  ]
}
```

**Validation / Business Rules**

- 본인 보유 쿠폰만 조회(S4).
- 유효기간 임박 쿠폰(예: 3일 이내[정책값])은 알림 발송 대상으로 별도 표시하거나 notification-service가 별도 배치로 처리(이 문서 범위 밖)[가정].
- 보유 쿠폰이 없으면 `content: []`.

---

## 에러 코드 매핑

`error-handling.md` 기준으로, 공통 코드는 `CommonErrorCode`를 그대로 쓰고 새 코드만 `OrderErrorCode`(도메인 전용, `implements ErrorCode`)에 추가합니다.

| 상황 | 코드 | HTTP | 관련 항목 |
| --- | --- | --- | --- |
| 재고 부족 | `OrderErrorCode.INSUFFICIENT_STOCK`(신규) | 409 `CONFLICT` | ORDER-003 |
| 쿠폰 재고(remaining_quantity) 소진 | `OrderErrorCode.COUPON_EXHAUSTED`(신규) | 409 `CONFLICT` | ORDER-012 |
| 재입고 알림 중복 신청 | 에러 아님 — idempotent 200 | - | ORDER-011 |
| 쿠폰 최소금액 미달/만료/미보유/1인한도초과 | `OrderErrorCode.COUPON_NOT_APPLICABLE`(신규) | 422 `BUSINESS_RULE_VIOLATION` | ORDER-002, ORDER-010, ORDER-012 |
| 같은 issuer_type 쿠폰 2개 이상 지정 | `CommonErrorCode.INVALID_INPUT`(기존) | 400 | ORDER-002, ORDER-003 |
| 주문 상태상 취소 불가(마감 후 등) | `OrderErrorCode.ORDER_NOT_CANCELLABLE`(신규) | 422 `BUSINESS_RULE_VIOLATION` | ORDER-014 |
| 만료된(PAYMENT_EXPIRED) 주문에 대한 조작 시도 | `CommonErrorCode.RESOURCE_EXPIRED`(기존) | 410 | ORDER-003 이후 흐름, ORDER-014 |
| 타인 주문/리워드 접근 | `CommonErrorCode.FORBIDDEN`(기존) | 403 | ORDER-005, ORDER-014 |
| 예산 한도 초과 쿠폰 발급 | `OrderErrorCode.COUPON_BUDGET_EXCEEDED`(신규) | 422 `BUSINESS_RULE_VIOLATION` | ORDER-008 |
| project-service/live-service 등 외부 연동 실패 | `CommonErrorCode.DEPENDENCY_FAILURE`(기존) | 503 | ORDER-001, ORDER-012 |

> 실제 구현 시 `services/order-service/docs/OrderDomainErrorCodeMapping.md`(auth-service/project-service와 동일한 별도 문서)로 분리하는 것을 권장합니다 — 이 표는 최소 요약입니다.

---

## 이벤트 발행/구독

REST로 노출되지 않는 배치·이벤트 기반 기능(ORDER-006, 007, 013, 015, 016)은 아래와 같이 연결됩니다.

| 기능 ID | 유형 | 이벤트/트리거 | 방향 |
| --- | --- | --- | --- |
| ORDER-006 | 스케줄러 → 이벤트 발행 | 프로젝트 펀딩 마감 시각 도래 → `FundingGoalFailed` / `FundingSucceeded` 발행 | order-service → (payment-service 등 구독) |
| ORDER-007 | 이벤트/스케줄러 구독 | 신규가입(member-service)/등급산정/이벤트조건 → 플랫폼 쿠폰 자동 발급 | member-service(또는 스케줄러) → order-service |
| ORDER-013 | 스케줄러 | `payment_expires_at` 경과한 `PENDING` 주문 → `PAYMENT_EXPIRED` 전이 및 재고 원복 | 내부 배치 |
| ORDER-014 | API 호출 후 이벤트 발행 | 참여 취소 확정 → `FundingCancelledByMember` 발행 | order-service → payment-service 구독 |
| ORDER-015 | 이벤트 구독 | `PaymentCompleted`/`RefundCompleted` → 쿠폰 사용완료/복원 처리 | payment-service → order-service 구독 |
| ORDER-016 | 이벤트 구독 | `RewardCreated`/`RewardUpdated`(project-service 아웃박스) → `inventories` 동기화(⚠️ 절대값 덮어쓰기 금지, 증분 반영 필요 — 아래 반영 이력 참고) | project-service → order-service 구독 |

---

## 반영 이력

- **[신규 발견, 2026-09-07] `RewardCreated`/`RewardUpdated` 구독 필요**: project-service가 이미 아웃박스로 발행 중인 이벤트인데 order-service 쪽 구독 기능이 기능명세서에 없어 `OrderDomainFunctionalSpec.md`에 ORDER-016으로 추가(GitHub 코드 확인: `RewardEventPublisher`, `RewardEventOutboxWorker`).
- **[신규 발견, 잠재 버그, 2026-09-07] `RewardUpdated` 절대값 덮어쓰기 금지**: `RewardUpdatedEvent`(rewardId, projectId, isLimited, quantity)를 그대로 `available_stock=quantity`로 덮어쓰면 이미 판매된 수량이 사라진다(예: 100개 중 60개 판매 후 판매자가 한도를 150개로 늘리면 `available_stock`은 90(=150-60)이 되어야 하는데, 절대값 덮어쓰기는 150으로 만들어 60개가 부활함). project-service의 현재 이벤트 페이로드에는 변경 전 값이 없어, `previousQuantity`를 이벤트에 추가하거나 order-service가 `inventories.initial_quantity`를 별도로 보관해 증분 계산해야 함 — `OrderDomainFunctionalSpec.md` ORDER-016 검토의견 참고. **ERD에도 `inventories.initial_quantity BIGINT` 컬럼 추가를 권장**(이번 ERD 문서에는 미반영, 구현 전 추가 필요).
- **[설계 가정] ORDER-002/ORDER-010 통합**: 원 기능명세서의 "fundingId(임시 주문서)"라는 표현을 근거로 별도 영속 초안 엔티티를 가정하지 않고, 하나의 stateless 미리보기 엔드포인트(`POST /orders/preview`)로 설계함. 실제 의도와 다르면 확인 필요.
- **[설계 가정] 배송 진행 상태(제작착수~배송완료)**: shipping-service 소관으로 보고 이 문서에서는 주문의 결제/취소/환불 관련 상태(fundings.status)만 다룸.

## ⚠️ 남은 확인 필요 사항

- **미결제 주문 만료 유예시간(N분)**: ORDER-013/`payment_expires_at` 설정값 — PM 정책 확인 필요.
- **재입고 알림 신청 기능 중복**: 위 7번 엔드포인트 참고 — member-service `MvpImplementationSummary.md`의 MEMBER-008과 소유권 정리 필요.
- **일반 쿠폰(GENERAL) "받기" 액션 허용 여부**: 9번 엔드포인트가 라이브 쿠폰 전용인지 전체 쿠폰 공용인지 확인 필요.
- **`fundings`에 프로젝트명 스냅샷 컬럼 필요 여부**: 4번 엔드포인트(`GET /orders`)에서 매번 project-service를 실시간 조회할지, 스냅샷 컬럼을 ERD에 추가할지 결정 필요.
- **서포터 활동 목록의 공개설정 조회 방식**: member-service 동기 호출 vs `fundings` 생성 시점 스냅샷 — 1번 엔드포인트 참고.

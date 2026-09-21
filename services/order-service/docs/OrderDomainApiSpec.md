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
| 11 | GET | `/api/v1/inventories/{rewardId}` | 잔여재고 조회(project-service 동기 호출) | X (서비스 간, 게이트웨이 미라우팅) | PROJECT-028 |
| 12 | GET | `/internal/fundings/{fundingId}` | 내부 펀딩 스냅샷 조회(레거시 Long PK) | 내부 키 (`InternalGatewaySecretFilter`) | payment/fulfillment v1 연동 |
| 13 | GET | `/internal/orders/{orderId}` | 내부 펀딩 스냅샷 조회(orderId UUID) | 내부 키 (`InternalGatewaySecretFilter`) | payment/fulfillment v2 연동 |
| 14 | GET | `/internal/projects/{projectId}/funding-participants` | 내부 펀딩 성립 참여자 조회 | 내부 키 (`InternalGatewaySecretFilter`) | fulfillment 연동 |
| 15 | GET | `/internal/orders/order-summaries` | 내부 주문 요약 배치 조회(프로젝트명·라인아이템) | 내부 키 (`InternalGatewaySecretFilter`) | payment 연동(V04) |
| 16 | GET | `/internal/fundings/{fundingId}/settlement-aggregate` | 내부 정산 집계 조회(리워드·옵션별 판매수량/금액, 메이커 쿠폰 차감액) | 내부 키 (`InternalGatewaySecretFilter`) | payment 연동(PAYMENT-009/012) |
| 17 | GET | `/api/v1/projects/{projectId}/orders` | 판매자 발송 대상 목록 | O (판매자) | 발송 등록 화면 |

> ORDER-006(펀딩 마감 목표달성 판정), ORDER-007(쿠폰 발급-플랫폼 자동), ORDER-013(미결제 주문 자동 만료), ORDER-016(리워드 이벤트 구독-재고 동기화)은 스케줄러/이벤트로만 트리거되어 REST 엔드포인트가 없습니다. ORDER-015(쿠폰 사용처리·복원)는 `PaymentEventKafkaListener`가 `payment.completed.v1`/`refund.completed.v1`을 구독해 실제로 배선되어 있습니다. 하단 "이벤트 발행/구독" 섹션에 정리했습니다.
>
> **식별자 계약 (cross-service ID 통일 #69)**:
> - 공개 REST의 `orderId`는 `fundings.public_id`(UUID).
> - 공개 REST의 `projectId`(`POST /orders`·`/preview`·목록 응답)는 project-service `publicId`(UUID). 클라이언트가 프로젝트 조회 응답의 `projectId`를 그대로 넘긴다 — 서버 쪽 내부 Long PK 해석 호출이 없다. 레거시 Long `projectId` 계약(`/api/v2/orders`로 먼저 도입됐던 것)은 폐기하고 `/api/v1/orders`에 통합했다.
> - Kafka `fundingId`는 여전히 `fundings.id`(Long PK). 같은 메시지에 `orderId`(UUID)·`projectPublicId`(UUID)를 필드 추가로 실어 둔다.

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
    { "displayName": "구매자", "amount": 39000, "relativeTime": "3분 전" },
    { "displayName": "익명", "amount": null, "relativeTime": "10분 전" }
  ],
  "page": 0, "size": 20, "totalElements": 128, "totalPages": 7, "hasNext": true
}
```

**Validation / Business Rules**

- 최신순 정렬, 무한스크롤(페이지네이션).
- 공개설정은 member-service를 요청마다 동기 호출(`MemberDisclosureClient`)한다. 동의하지 않은 참여자는 `displayName`을 `"익명"`으로, `amount`를 `null`로 마스킹한다(S9). 동의한 참여자도 실제 닉네임은 조회하지 않고 `displayName`을 `"구매자"`로 일반화한다.
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
  "projectId": "018f9a1b-....-....-............",
  "lineItems": [
    { "rewardId": 1, "quantity": 1, "optionValueIds": [100] }
  ],
  "shippingAddress": {
    "recipientName": "홍길동", "phoneNumber": "010-1234-5678",
    "zipcode": "12345", "addressLine1": "서울시 ...", "addressLine2": "101동 101호"
  },
  "couponCodes": ["WELCOME2026", "PJT123-A1B2"],
  "autoApplyBestCoupon": false
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

- **비영속(stateless) 계산 전용 엔드포인트.** 호출해도 DB에 아무것도 생성되지 않는다 — ORDER-003(주문 생성) 전까지 사용자가 리워드/배송지/쿠폰을 바꿀 때마다 반복 호출해 최종 금액을 미리 확인하는 용도(PRD 13.2). "임시 주문서" 레코드를 만들지 않는다.
- 입력은 `projectId` + `lineItems`(rewardId/quantity/optionValueIds) + `shippingAddress` + 선택 `couponCodes`. `fundingId`를 받지 않는다. `shippingAddress`는 DTO에서 필수(`@NotNull`)이나 미리보기 계산에는 사용하지 않는다(배송비는 고정값).
- `couponCodes`를 생략하면 할인 없이 계산(ORDER-002 단독 동작), 포함하면 해당 쿠폰들을 적용해 재계산(ORDER-010 동작을 겸함).
- `couponCodes`는 **최대 2개, 그것도 `issuer_type`이 서로 달라야 함**(플랫폼쿠폰 1개 + 메이커쿠폰 1개까지만 — 16.3.3). 같은 issuer_type을 2개 보내면 `400 INVALID_INPUT`, 3개 이상 보내면 `400 INVALID_INPUT`.
- **`autoApplyBestCoupon: true`(16.3.4 최적 쿠폰 추천)**면 `couponCodes`를 무시하고, 회원이 보유한(AVAILABLE) 쿠폰 중 조건을 만족하는 것에서 발급주체(플랫폼/메이커)별로 할인액이 가장 큰 것 하나씩만(최대 2개) 자동 적용한다. FE가 코드를 나열할 필요가 없다. 이 경로에서 탈락한 후보는 사용자가 직접 요청한 적이 없어 `unavailableCoupons`에 나열하지 않는다(applied만 채워짐). 기본값은 `false`(생략 시 기존 명시적 적용 동작 그대로).
- 쿠폰의 `targetScope`(대상)가 `CATEGORY`/`MAKER`면 project-service를 조회해 실제로 프로젝트 카테고리/판매자와 일치하는지 검증한다(`PROJECT`/`ALL`은 조회 없이 판단). 불일치하면 `NOT_APPLICABLE`.
- 금액 계산은 항상 서버에서 수행(리워드 단가·배송비·쿠폰 할인율 모두 서버 조회값 사용), 클라이언트가 보낸 금액을 신뢰하지 않는다(S4).
- 리워드금액 합산 + **배송비 고정 3,000원**(`order.policy.default-shipping-fee`) + 쿠폰 할인(`FREE_SHIPPING`은 배송비 한도 내에서 할인, `coupons.max_discount_amount` 설정 시 그 한도까지) 반영. 프로젝트/리워드별 배송비 정책은 아직 없어 고정값이다.
- 쿠폰이 최소 펀딩금액 미달·만료·본인 미보유 등으로 적용 불가능하면 해당 쿠폰은 `appliedCoupons`에 넣지 않고 `unavailableCoupons`에 사유 코드로 안내한다(예: `MIN_AMOUNT_NOT_MET`, `EXPIRED`, `NOT_OWNED`, `ALREADY_USED`, `NOT_FOUND`, `BUDGET_EXCEEDED`, `NOT_APPLICABLE`). preview 자체는 422를 내지 않는다.
- **[알려진 제약] 쿠폰 2개(플랫폼+메이커) 동시 적용 시 결제완료/환불 이벤트는 첫 번째 쿠폰만 반영한다.** 결제 시점의 `payment.completed.v1`/`refund.completed.v1` payload(`couponIssuanceId`)가 단수라, `funding_coupon_applications`에 2건이 있어도 사용확정(USED)·환불복원(AVAILABLE) 처리는 첫 번째 건에만 적용된다(`FundingInternalQueryService.toSnapshot()` 주석 참고). 두 번째 쿠폰의 실제 최종 상태는 이 API들의 응답만으로는 확인할 수 없고, 향후 이벤트 payload를 복수 지원으로 바꿔야 해결된다(별도 이슈, 이번 범위 밖).

---

### 3. 펀딩 주문 생성(재고 검증/차감)

```
POST /api/v1/orders
```

**Auth Required**: O (구매자)

**Request**: 미리보기(`/orders/preview`)와 동일한 바디(리워드/옵션/수량, 배송지, `couponCodes`(선택, 최대 2개 — 위 2번 엔드포인트 규칙과 동일)). 배송지는 여기서 실제 저장된다.

**Response**: `201 Created`

```json
{
  "orderId": "018f9a1b-....-....-............",
  "status": "PENDING",
  "finalAmount": 39000,
  "paymentExpiresAt": "2026-09-07T10:30:00Z"
}
```

**Validation / Business Rules**

- 하나의 트랜잭션으로 `inventories.available_stock`을 대상으로 재고 검증 및 조건부 UPDATE(낙관적 락, `version` 컬럼)로 차감 → `fundings`(status=`PENDING`, `payment_expires_at` = 생성시각 + **30분**, `order.policy.payment-expiry-minutes`) + `funding_line_items` (+ 옵션 스냅샷 `funding_line_item_options`) 생성. `project_title`은 project-service에서 조회해 스냅샷으로 저장한다.
- **`reserved_stock`은 사용하지 않는다.** 차감·원복은 `available_stock`만 건드리고, `reserved_stock`은 생성 시 0으로 두고 이후에도 갱신하지 않는다.
- 재고 부족 시 `409 CONFLICT`(`INSUFFICIENT_STOCK`), 트랜잭션 롤백(재고 변경 없음) — 부족한 `rewardId`는 메시지에 포함한다(`ErrorResponse.detail`은 null).
- 쿠폰 적용 시 `funding_coupon_applications` 생성 및 `coupons.used_budget_amount` 갱신. `coupon_issuances.status`는 아직 변경하지 않음(사용확정 처리는 ORDER-015가 결제완료 이벤트로 수행할 예정 — 현재 리스너 미배선).
- 금액은 서버에서 재검증(클라이언트 전달값 불신, S4), 재고 차감 쿼리는 바인딩 변수 사용(S1).
- 응답의 `orderId`는 `fundings.public_id`(UUID), `projectId`는 project-service publicId(UUID). 이후 결제 요청은 payment-service의 `POST /api/v2/payments`(`fundingId`=이 `orderId`)로 이어진다.

---

### 4. 내 펀딩 참여 목록 조회

```
GET /api/v1/orders
```

**Auth Required**: O (구매자)

**Request**: Query Parameter: `status`(선택, `FundingStatus` enum), `page`, `size`(선택, 기본 0/20)

**Response Body**

```json
{
  "content": [
    {
      "orderId": "018f9a1b-....",
      "projectId": "018f2c1a-....", "projectTitle": "세상에 없는 프라이팬",
      "status": "FUNDING_IN_PROGRESS", "discountAmount": 3000, "finalAmount": 39000,
      "createdAt": "2026-09-07T10:00:00Z",
      "sellerDisplayName": "메이커", "thumbnailUrl": "https://cdn.fundit.example/x.png",
      "rewardSummary": "얼리버드 패키지 외 1건", "totalQuantity": 3,
      "availableActions": ["CANCEL"]
    }
  ],
  "page": 0, "size": 20, "totalElements": 3, "totalPages": 1, "hasNext": false
}
```

**Validation / Business Rules**

- `member_id` 기준 본인 참여 내역만 조회(S4).
- `status` 파라미터 미지정 시 전체 상태 반환.
- **`finalAmount`는 상세와 동일하게 쿠폰을 적용한다.** `totalRewardAmount + shippingFee - discountAmount`. `discountAmount`는 `funding_coupon_applications` 합산.
- `projectTitle`은 주문 생성 시점 스냅샷(`fundings.project_title`). 조회 시 project-service를 다시 호출하지 않는다.
- `sellerDisplayName`/`thumbnailUrl`은 project-service 내부 배치 API(`GET /internal/projects/summaries`)로 페이지 단위 1회 조회해 채운다(건별 재호출 없음, V03). 조회 실패 시 둘 다 null. `sellerDisplayName`은 project-service의 `SellerProfileClient`가 아직 member-service 연동 전 스텁이라 현재는 항상 null이다(project-service CLAUDE.md 참고).
- `rewardSummary`는 주문에 담긴 첫 리워드명 기준 `"{첫 리워드명}"` 또는(2건 이상) `"{첫 리워드명} 외 N건"`. `totalQuantity`는 라인아이템 수량 합계.
- `availableActions`는 아래 상세 API(`GET /api/v1/orders/{orderId}`) 설명의 `availableActions` 규칙과 동일하다. 다만 `GOAL_ACHIEVED` 건의 배송 상태는 fulfillment-service 내부 배치 API(`GET /internal/fundings/fulfillment-statuses`)로 페이지 단위 1회 조회한다(상세 API는 단건 API `.../fulfillment-status`를 쓴다).

---

### 5. 개별 펀딩 참여 상세 조회

```
GET /api/v1/orders/{orderId}
```

**Auth Required**: O (구매자)

**Request**: Path Parameter: `orderId`(public_id, UUID)

**Response Body**

```json
{
  "orderId": "018f9a1b-....",
  "status": "FUNDING_IN_PROGRESS",
  "lineItems": [
    { "rewardId": 1, "rewardName": "얼리버드 패키지", "quantity": 1, "unitPrice": 39000,
      "options": [ { "optionGroupName": "색상", "optionValue": "화이트" } ] }
  ],
  "shippingFee": 3000, "discountAmount": 3000, "finalAmount": 39000,
  "couponLifecycleScope": "FIRST_ONLY",
  "shippingAddress": {
    "recipientName": "홍길동", "phoneNumber": "010-1234-5678",
    "zipcode": "12345", "addressLine1": "...", "addressLine2": "101동 101호"
  },
  "availableActions": ["CANCEL"]
}
```

**Validation / Business Rules**

- `orderId`(public_id) 소유권 서버 검증 — 타인 주문 접근 시 `403 FORBIDDEN`(S4). 없으면 `404 NOT_FOUND`.
- **`finalAmount`는 쿠폰을 적용한다.** `totalRewardAmount + shippingFee - discountAmount`. 목록 API와 계산식이 같다.
- **`couponLifecycleScope`는 항상 `FIRST_ONLY`.** 주문에 플랫폼+메이커 쿠폰이 같이 있어도 `payment.completed.v1`/`refund.completed.v1`의 `couponIssuanceId`는 단수라, 사용확정(USED)·환불복원(AVAILABLE)은 **첫 번째 적용 쿠폰만** 처리된다. 두 번째 쿠폰은 결제 후에도 `AVAILABLE`로 남을 수 있고, 환불 시에도 복원되지 않는다. 복수 쿠폰 이벤트 계약은 별도 이슈.
- `paidAt`은 payment-service 소관이라 order-service는 값을 알지 못해 항상 null이고, `non_null` 직렬화 설정으로 JSON에서 필드가 생략된다.
- `availableActions`는 `status`에 따라 계산: `PENDING`/`FUNDING_IN_PROGRESS` → `["CANCEL"]`. `GOAL_ACHIEVED`는 fulfillment-service(FULFILLMENT-008, `GET /internal/fundings/{fundingId}/fulfillment-status`) 조회 결과로 세분화 — 배송 시작 전(`isAlreadyShipped=false`) → `["SHIPPING_DELAY_REFUND_REQUEST"]`, 배송완료(`deliveredAt != null`) → `["DEFECT_REFUND_REQUEST"]`, 그 사이(발송됐지만 미배송) → `[]`. 그 외 상태(`PAYMENT_EXPIRED`/`CANCELLED_BY_MEMBER`/`GOAL_FAILED_REFUNDED`/`REFUNDED_AFTER_SUCCESS`) → `[]`.
- 불가능한 액션 시도 시(예: 마감 후 취소) → `422 BUSINESS_RULE_VIOLATION`(`ORDER_NOT_CANCELLABLE`).

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

- `fundings.status`가 `PENDING` 또는 `FUNDING_IN_PROGRESS`(=펀딩 진행중, 마감 전)인지 검증 → `status='PAYMENT_EXPIRED'`이면 `410 RESOURCE_EXPIRED`("이미 만료된 주문입니다"), 그 외(성립/미달 판정 이후 상태)는 `422 BUSINESS_RULE_VIOLATION`(`ORDER_NOT_CANCELLABLE`, "펀딩이 종료되어 취소할 수 없습니다").
- 검증 통과 시 `status=CANCELLED_BY_MEMBER`로 전이하고 차감했던 `available_stock`을 원복, `funding.cancelled-by-member.v1` 이벤트 발행(payment-service가 구독해 실제 결제취소·환불 실행, ORDER-014/PAYMENT-004 참고).
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
- 재입고(품절에서 `available_stock`이 다시 양수가 됨) 시 order-service가 신청자마다 `notification.raised.v1`(`notifType=REWARD_RESTOCK`)을 발행하고 신청 레코드를 삭제한다. notification-service 배치가 이 테이블을 직접 읽지 않는다.
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
  "expiresAt": "2026-09-30T23:59:59Z"
}
```

**Response**: `201 Created`

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

- 본인 소유 프로젝트에만 발급 가능 — `projectId`의 `seller_id`를 project-service 내부 API로 조회해 검증(S4). 불일치 시 `403 FORBIDDEN`, 프로젝트 없음 `404 NOT_FOUND`.
- `discountType='FREE_SHIPPING'`이면 `discountValue=0`으로 저장, 적용 시점에 해당 주문의 `shippingFee`가 할인액이 됨.
- 발급 개수 전량이 최대 한도로 사용돼도 `budgetLimit`을 넘는 명백한 경우는 발급 시점에 `422 COUPON_BUDGET_EXCEEDED`로 차단한다. 그 외(정률·무료배송처럼 상한을 알 수 없는 경우)는 발급을 허용하고 적용 단계에서 예산 소진 시 노출 제외.
- `couponCode`는 서버에서 생성(예: `PJT{projectId}-{랜덤4자리}`), 클라이언트가 지정하지 않음.
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
{ "couponCode": "LIVE-XY12", "issued": true, "expiresAt": "2026-09-07T21:00:00Z" }
```

**Validation / Business Rules**

- `coupons.remaining_quantity`를 `version` 낙관적 락으로 조건부 차감(`UPDATE ... WHERE remaining_quantity > 0 AND version = :v`) 후 `coupon_issuances` 생성 — 갱신 실패(동시 소진) 시 짧게 재시도, 최종 실패 시 `409 CONFLICT`("쿠폰이 모두 소진되었습니다").
- `per_member_limit` 초과 발급 시도 → `422 COUPON_NOT_APPLICABLE`.
- `issue_channel='LIVE'`인 쿠폰의 "방송 진행 중" 검증은 live-service 연동 전이라 **생략**한다. GENERAL/LIVE 구분 없이 동일 엔드포인트로 클레임한다.

---

### 10. 쿠폰함 조회

```
GET /api/v1/coupons/me
```

**Auth Required**: O (구매자)

**Request**: Query Parameter: `status`(선택, `AVAILABLE`\|`USED`\|`EXPIRED`), `page`, `size`(선택, 기본 0/20)

**Response Body**: `PageResponse` (`content` + 페이지 메타)

```json
{
  "content": [
    {
      "couponCode": "WELCOME2026", "couponName": "신규가입 축하 쿠폰",
      "discountType": "AMOUNT", "discountValue": 3000,
      "status": "AVAILABLE", "expiresAt": "2026-12-31T23:59:59Z",
      "minFundingAmount": 0, "perMemberLimit": 1, "targetScope": "ALL", "targetRefId": null,
      "issuerType": "PLATFORM", "maxDiscountAmount": 5000
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false
}
```

**Validation / Business Rules**

- 본인 보유 쿠폰만 조회(S4).
- `minFundingAmount`/`perMemberLimit`/`targetScope`(`ALL`\|`CATEGORY`\|`PROJECT`\|`MAKER`)/`targetRefId`로 사용 조건·적용 대상을 노출한다(PRD 16.2.4).
- `issuerType`(`PLATFORM`\|`MAKER`)은 발급 주체, `maxDiscountAmount`는 정률 쿠폰 최대 할인액(없으면 필드 생략).
- 유효기간 임박(기본 3일 이내, `order.batch.coupon-expiring-reminder.window-days`) 쿠폰은 order-service 배치가 `notification.raised.v1`(`notifType=COUPON_EXPIRING`)을 발행한다. notification-service가 별도 배치로 쿠폰함을 읽지 않는다.
- 보유 쿠폰이 없으면 `content: []` + 페이지 메타(`totalElements: 0`).

---

### 11. 잔여재고 조회(서비스 간)

```
GET /api/v1/inventories/{rewardId}
```

**Auth Required**: X — `@LoginUser` 없음. `InternalEndpointConfig`에도 등록하지 않아 `X-Internal-Api-Key`를 요구하지 않는다.

**호출 주체**: project-service(PROJECT-028). 게이트웨이 `application.yml`이 이 경로를 order-service 라우트에 **넣지 않아** 외부(브라우저)로는 노출되지 않는다. project-service가 order-service를 직접 호출한다.

**Request**: Path Parameter: `rewardId`(Long)

**Response Body**

```json
{ "rewardId": 1, "remainingStock": 42 }
```

무제한 리워드이거나 재고 원장 행이 아직 없으면:

```json
{ "rewardId": 1 }
```

(`remainingStock`이 null이면 Jackson `non_null` 설정으로 필드가 생략된다. 의미는 **unlimited / no ledger**.)

**Validation / Business Rules**

- `inventories.available_stock`을 그대로 반환한다. 행이 없으면 empty → `remainingStock=null`.
- 존재하지 않는 rewardId도 404가 아니라 `{ rewardId, remainingStock: null }`이다.

---

### 12. 내부 펀딩 스냅샷 조회

```
GET /internal/fundings/{fundingId}
```

**Auth Required**: 내부 전용. 게이트웨이가 `/internal/**`를 order-service로 라우팅하지 않아 외부 노출이 차단되고, 서비스 쪽 `InternalGatewaySecretFilter`가 `InternalEndpointConfig`에 등록된 이 경로에 `X-Internal-Api-Key`를 요구한다.

**호출 주체**: payment-service / fulfillment-service v1 어댑터. Path의 `fundingId`는 **Long PK**(`fundings.id`). UUID 조회는 `GET /internal/orders/{orderId}`를 쓴다.

**Request**: Path Parameter: `fundingId`(Long)

**Response Body**

```json
{
  "fundingId": 1024,
  "projectId": "018f2c1a-3b4e-7a12-9c9d-0a1b2c3d4e5f",
  "memberId": "018f9a1b-....",
  "fundingPublicId": "018f9a1b-....",
  "sellerId": "018f2c1a-....",
  "status": "PENDING",
  "finalAmount": 35100,
  "orderName": "얼리버드 패키지 외 1건",
  "couponIssuanceId": 5,
  "appliedCouponCount": 2,
  "couponLifecycleScope": "FIRST_ONLY",
  "shippingFee": 3000,
  "discountAmount": 2000
}
```

**Validation / Business Rules**

- 없는 `fundingId` → `404 NOT_FOUND`.
- `projectId`는 project-service `publicId`(UUID). `fundingPublicId`는 외부 노출 `orderId`.
- `finalAmount`는 `totalRewardAmount + shippingFee - discountAmount`(`funding_coupon_applications` 합산). `orderName`은 첫 번째 라인아이템 리워드명 기준("리워드명" 또는 2건 이상이면 "리워드명 외 N건")으로 만든 표시용 문자열이다.
- `shippingFee`/`discountAmount`는 위 `finalAmount` 계산식의 구성요소를 그대로 노출한다(R05 — payment-service 환불 예상금액 사전계산용). 둘 다 신규 추가 필드라 기존 소비자(`HttpOrderFundingClient`)는 무시해도 무방(api-convention.md "필드 추가는 버전을 올리지 않음").
- `couponIssuanceId`는 적용 쿠폰 중 **첫 번째 건만**(단수). `appliedCouponCount`가 2여도 결제완료/환불 이벤트는 이 ID 하나만 실어 보낸다. `couponLifecycleScope`=`FIRST_ONLY`가 그 제약을 응답에 명시한다.
- payment-service `HttpOrderFundingClient`(PAYMENT-001)가 이 응답 그대로를 소비한다. 필드명을 바꾸면 그쪽 역직렬화가 깨진다.

---

### 13. 내부 펀딩 스냅샷 조회(orderId UUID)

```
GET /internal/orders/{orderId}
```

**Auth Required**: 12번과 동일(게이트웨이 미라우팅 + `InternalGatewaySecretFilter`).

**호출 주체**: payment-service / fulfillment-service v2. Path의 `orderId`는 `fundings.public_id`(UUID).

**Request**: Path Parameter: `orderId`(UUID)

**Response Body**: 12번과 동일.

**Validation / Business Rules**

- 없는 `orderId` → `404 NOT_FOUND`.

---

### 14. 내부 펀딩 성립 참여자 조회

```
GET /internal/projects/{projectId}/funding-participants
```

**Auth Required**: 내부 전용. 12번과 동일하게 게이트웨이 미라우팅 + `InternalGatewaySecretFilter`(`X-Internal-Api-Key`).

**호출 주체**: fulfillment-service(알림 팬아웃 대상).

**Request**: Path Parameter: `projectId`(UUID, project-service publicId)

**Response Body**

```json
{ "memberIds": ["018f9a1b-....", "018f9a1c-...."] }
```

**Validation / Business Rules**

- `status=GOAL_ACHIEVED`인 참여자의 `memberId` 목록. 성립 후 전액 환불(`REFUNDED_AFTER_SUCCESS`)된 건은 제외.
- 참여자가 없으면 `{ "memberIds": [] }`.

---

### 15. 내부 주문 요약 배치 조회

```
GET /internal/orders/order-summaries?orderIds={orderId1},{orderId2},...
```

**Auth Required**: 내부 전용. 12번과 동일하게 게이트웨이 미라우팅 + `InternalGatewaySecretFilter`(`X-Internal-Api-Key`).

**호출 주체**: payment-service 환불 목록(`GET /api/v1/refunds`, V04) — 페이지 단위로 한 번만 호출해 N+1을 피한다.

**Request**: Query Parameter: `orderIds` — orderId(UUID) 목록(반복 파라미터).

**Response Body**

```json
[
  {
    "orderId": "018f9a1b-....",
    "projectTitle": "세상에 없는 프라이팬",
    "lineItems": [
      { "rewardId": 1, "rewardName": "얼리버드 패키지", "quantity": 2, "unitPrice": 10000, "options": [] }
    ]
  }
]
```

**Validation / Business Rules**

- 존재하지 않는 `orderId`는 결과에서 조용히 빠진다(요청한 개수보다 응답 배열이 짧을 수 있다) — 배치 API는 부분 실패를 에러로 취급하지 않는다.
- `lineItems`는 `GET /api/v1/orders/{orderId}` 상세 응답의 `lineItems`와 동일한 구조(`OrderLineItemDetailResponse`)를 그대로 재사용한다.
- 쿠폰 할인·최종 결제액은 포함하지 않는다 — payment-service 자체 `payments.amount`가 이미 최종 결제액을 갖고 있어 중복 계산하지 않는다.

---

### 16. 내부 정산 집계 조회

```
GET /internal/fundings/{fundingId}/settlement-aggregate
```

**Auth Required**: 내부 전용. 12번과 동일하게 게이트웨이 미라우팅 + `InternalGatewaySecretFilter`(`X-Internal-Api-Key`).

**호출 주체**: payment-service 정산(`GET /api/v1/settlements/{settlementBatchId}` PAYMENT-009, 선정산/최종정산 배치 생성 PAYMENT-012) — 배치 항목(`settlement_batch_items`)마다 건별로 호출한다.

**Request**: Path Variable: `fundingId`(Long PK, `fundings.id`)

**Response Body**

```json
{
  "lineItems": [
    { "rewardId": 1, "rewardName": "얼리버드 패키지", "optionName": "색상: 화이트, 사이즈: L", "quantity": 2, "amount": 20000 }
  ],
  "makerCouponDeductionAmount": 3000
}
```

**Validation / Business Rules**

- `lineItems`는 `funding_line_items`/`funding_line_item_options` 주문 시점 스냅샷 그대로다(리워드/옵션명이 나중에 바뀌어도 과거 정산 내역은 불변). 옵션이 여러 개면 `"그룹명: 값"`을 쉼표로 이어붙인다. 옵션이 없으면 `optionName`은 `null`.
- `makerCouponDeductionAmount`는 `funding_coupon_applications`를 `coupon_issuances`→`coupons`로 조인해 `issuer_type='MAKER'`인 적용분만 합산한다(PRD 16.5.3 — 플랫폼 발급 쿠폰은 플랫폼이 부담하므로 메이커 정산에서 차감하지 않는다).
- 존재하지 않는 `fundingId` → `404 NOT_FOUND`.

---

### 17. 판매자 발송 대상 목록

```
GET /api/v1/projects/{projectId}/orders
```

**Auth Required**: O (판매자)

**게이트웨이**: `/api/v1/projects/*/orders/**` 를 order-service로 라우팅(project-service `/api/v1/projects/**`보다 먼저).

**Response Body**

```json
[
  {
    "orderId": "018f9a1b-....",
    "lineItems": [
      { "rewardId": 1, "rewardName": "얼리버드 패키지", "quantity": 1, "unitPrice": 39000,
        "options": [ { "optionValueId": 100, "optionGroupName": "색상", "optionValue": "화이트" } ] }
    ],
    "shippingAddress": {
      "recipientName": "홍길동", "phoneNumber": "010-1234-5678",
      "zipcode": "12345", "addressLine1": "...", "addressLine2": "101동 101호"
    }
  }
]
```

**Validation / Business Rules**

- 프로젝트 소유권은 project-service 조회로 검증(S4). 타인 프로젝트 `403`, 없음 `404`.
- `status=GOAL_ACHIEVED` 건만 반환(발송 대상). 빈 목록은 `[]`.
- fulfillment-service는 단건 발송 등록 API만 제공한다. 목록은 이 엔드포인트를 FE가 직접 호출한다.

---

## 에러 코드 매핑

`error-handling.md` 기준으로, 공통 코드는 `CommonErrorCode`를 그대로 쓰고 새 코드만 `OrderErrorCode`(도메인 전용, `implements ErrorCode`)에 추가합니다.

| 상황 | 코드 | HTTP | 관련 항목 |
| --- | --- | --- | --- |
| 재고 부족 | `OrderErrorCode.INSUFFICIENT_STOCK` | 409 `CONFLICT` | ORDER-003 |
| 쿠폰 재고(remaining_quantity) 소진 | `OrderErrorCode.COUPON_EXHAUSTED` | 409 `CONFLICT` | ORDER-012 |
| 재입고 알림 중복 신청 | 에러 아님 — idempotent 200 | - | ORDER-011 |
| 쿠폰 최소금액 미달/만료/미보유/1인한도초과 | `OrderErrorCode.COUPON_NOT_APPLICABLE` | 422 `BUSINESS_RULE_VIOLATION` | ORDER-012(클레임). preview는 422 대신 `unavailableCoupons` |
| 같은 issuer_type 쿠폰 2개 이상 지정 / 쿠폰 3개 이상 | `CommonErrorCode.INVALID_INPUT` | 400 | ORDER-002, ORDER-003 |
| 주문 상태상 취소 불가(마감 후 등) | `OrderErrorCode.ORDER_NOT_CANCELLABLE` | 422 `BUSINESS_RULE_VIOLATION` | ORDER-014 |
| 만료된(PAYMENT_EXPIRED) 주문에 대한 조작 시도 | `CommonErrorCode.RESOURCE_EXPIRED` | 410 | ORDER-014 |
| 타인 주문 접근 | `CommonErrorCode.FORBIDDEN` | 403 | ORDER-005, ORDER-014, ORDER-008 |
| 예산 한도 초과 쿠폰 발급/적용 | `OrderErrorCode.COUPON_BUDGET_EXCEEDED` | 422 `BUSINESS_RULE_VIOLATION` | ORDER-008, ORDER-003 |
| project-service 등 외부 연동 실패 | `CommonErrorCode.DEPENDENCY_FAILURE` | 503 | ORDER-001, ORDER-003, ORDER-008 |

---

## 이벤트 발행/구독

REST로 노출되지 않는 배치·이벤트 기반 기능은 아래와 같이 연결됩니다. **공개 REST는 `orderId`(UUID, `public_id`)를 쓰고, 이벤트·내부 API는 `fundingId`(Long PK)를 씁니다.**

### 구독

| 기능 ID | 토픽 | 페이로드(실제 JSON) | 상태 |
| --- | --- | --- | --- |
| ORDER-006 | `project.funding-deadline-reached.v1` | `{ "projectId": 123, "goalAmount": 5000000 }` | `@KafkaListener` 구현됨. **project-service는 이 토픽을 아직 발행하지 않음** |
| ORDER-007 | `member.signed-up.v1` | `{ "memberId": "<UUID>" }` | `@KafkaListener` 구현됨. 웰컴 쿠폰 1종만(`order.policy.welcome-coupon-code`) |
| ORDER-016 | `reward.created.v1` / `reward.updated.v1` | `{ "rewardId": 1, "projectId": 123, "isLimited": true, "quantity": 100 }` | `@KafkaListener` 구현됨. `initial_quantity` 대비 델타 반영 |
| ORDER-015 | `payment.completed.v1` / `refund.completed.v1` | (payment-service 발행 계약) | **미배선.** `PaymentEventSyncService` 로직은 있으나 `@KafkaListener`가 없어 소비하지 않음 |

### 발행 (아웃박스 → Kafka, `eventId` = `"order:{outboxId}"`, 파티션 키 = `fundingId` 또는 `memberId`)

**`funding.succeeded.v1`** (ORDER-006, 성립 펀딩 **건마다** 1건. `sellerId`/`achievedAt` 포함)

```json
{
  "eventId": "order:42",
  "fundingId": 1024,
  "projectId": 123,
  "sellerId": "018f9a1b-....",
  "achievedAt": "2026-09-07T10:00:00Z"
}
```

**`funding.goal-failed.v1`** (ORDER-006, 미달 펀딩 **건마다** 1건)

```json
{
  "eventId": "order:43",
  "fundingId": 1025,
  "projectId": 123
}
```

**`funding.cancelled-by-member.v1`** (ORDER-014. 토픽명은 `funding.cancelled.v1`이 아님)

```json
{
  "eventId": "order:44",
  "fundingId": 1026,
  "projectId": 123,
  "memberId": "018f9a1b-...."
}
```

**`notification.raised.v1`** (재입고 ORDER-011 대기분 / 쿠폰 만료임박 ORDER-009. 파티션 키=`memberId`)

```json
{
  "eventId": "order:45",
  "memberId": "018f9a1b-....",
  "notifType": "REWARD_RESTOCK",
  "title": "신청하신 리워드가 재입고되었어요",
  "relatedUrl": "/rewards/1"
}
```

```json
{
  "eventId": "order:46",
  "memberId": "018f9a1b-....",
  "notifType": "COUPON_EXPIRING",
  "title": "보유하신 쿠폰이 곧 만료돼요",
  "relatedUrl": "/my/coupons"
}
```

**`project.funding-reward-stats-updated.v1`** (PROJECT-015, 1일 배치. 파티션 키=`projectId` 내부 Long. 전체 교체)

```json
{
  "eventId": "order:47",
  "projectId": 123,
  "rewardStats": [
    { "rewardId": 1, "optionValueId": 100, "purchasedQuantity": 2, "purchasedAmount": 78000 },
    { "rewardId": 1, "optionValueId": null, "purchasedQuantity": 1, "purchasedAmount": 39000 }
  ]
}
```

`optionValueId`가 null이면 옵션 없는 리워드 합계, 있으면 해당 옵션값 한정. 라인에 옵션이 여러 개면 옵션값마다 같은 수량/금액을 잡는다.

| 기능 ID | 유형 | 트리거 | 방향 |
| --- | --- | --- | --- |
| ORDER-006 | 이벤트 구독 → 건별 발행 | `project.funding-deadline-reached.v1` 수신 후 해당 프로젝트의 활성 펀딩마다 `funding.succeeded.v1` 또는 `funding.goal-failed.v1` | project-service → order-service → payment/fulfillment/search |
| ORDER-007 | 이벤트 구독 | `member.signed-up.v1` → 설정된 웰컴 쿠폰 1종 `autoIssue` | member-service → order-service |
| ORDER-013 | 스케줄러 | `payment_expires_at` 경과한 `PENDING` → `PAYMENT_EXPIRED` + `available_stock` 원복 | 내부 배치(60초) |
| ORDER-014 | API 호출 후 발행 | 참여 취소 확정 → `funding.cancelled-by-member.v1` | order-service → payment-service |
| ORDER-015 | 로직만 존재 | `PaymentEventSyncService`가 결제완료/환불완료 시 쿠폰 사용·복원 및 `PENDING`→`FUNDING_IN_PROGRESS` 전이를 수행하도록 짜여 있으나, Kafka 리스너가 없어 트리거되지 않음 | (미배선) |
| ORDER-016 | 이벤트 구독 | `reward.created.v1`/`reward.updated.v1` → `inventories` 델타 동기화. 품절→재입고 시 `notification.raised.v1` | project-service → order-service |
| PROJECT-015 | 스케줄러 후 발행 | 1일 배치 → `project.funding-reward-stats-updated.v1` (옵션값 단위 rewardStats) | order-service → project-service |

---

## 반영 이력

- **[구현 동기화, 2026-09-18]** payment-service 결제 연동 대응 + PM 쿠폰(16장) 정책 대조 결과 반영: `/internal/fundings/{fundingId}`·`/internal/orders/{orderId}` 응답에 `sellerId`/`status`/`finalAmount`/`orderName`/`couponIssuanceId` 추가(11/12번 항목, PAYMENT-001 연동 블로커 해소), 쿠폰함 응답에 `minFundingAmount`/`perMemberLimit`/`targetScope`/`targetRefId` 추가(10번 항목), `CATEGORY`/`MAKER` 쿠폰 스코프 실제 매칭 구현(project-service 카테고리 노출 포함), preview/생성 요청에 `autoApplyBestCoupon`(최적 쿠폰 자동 추천, 16.3.4) 추가.
- **[구현 동기화, 2026-09-17]** 현재 코드 기준으로 REST 누락분(재고 조회·내부 펀딩 API), 목록/상세 `finalAmount` 쿠폰 적용 차이, Kafka 실제 토픽·페이로드, ORDER-006 이벤트 구독, ORDER-015 리스너 미배선, ORDER-016 구현 완료, 배송비 3000원/만료 30분, `reserved_stock` 미사용, 쿠폰함 `PageResponse`, `notification.raised.v1` 발행을 반영.
- **[신규 발견, 2026-09-07] `RewardCreated`/`RewardUpdated` 구독 필요**: project-service가 이미 아웃박스로 발행 중인 이벤트인데 order-service 쪽 구독 기능이 기능명세서에 없어 `OrderFunctionalSpec.md`에 ORDER-016으로 추가(GitHub 코드 확인: `RewardEventPublisher`, `RewardEventOutboxWorker`). **→ 구현 완료.** `@KafkaListener`가 `reward.created.v1`/`reward.updated.v1`을 구독하고, `inventories.initial_quantity` 대비 델타로 `available_stock`을 갱신한다.
- **[신규 발견, 잠재 버그, 2026-09-07] `RewardUpdated` 절대값 덮어쓰기 금지**: `RewardUpdatedEvent`(rewardId, projectId, isLimited, quantity)를 그대로 `available_stock=quantity`로 덮어쓰면 이미 판매된 수량이 사라진다. **→ `initial_quantity` 컬럼 + 델타 반영으로 해소.**
- **[설계 확정] ORDER-002/ORDER-010 통합**: 임시 주문서(`fundingId`)를 만들지 않고, stateless 미리보기 `POST /orders/preview`(`projectId` + `lineItems` + 선택 `couponCodes`)로 동작한다.
- **[설계 가정] 배송 진행 상태(제작착수~배송완료)**: fulfillment-service 소관으로 보고 이 문서에서는 주문의 결제/취소/환불 관련 상태(fundings.status)만 다룸.

## ⚠️ 남은 확인 필요 사항

- **ORDER-015 Kafka 리스너 미배선**: `PaymentEventSyncService`는 있으나 `@KafkaListener`가 없어 결제완료/환불완료 이벤트를 소비하지 않는다. 붙이기 전까지 쿠폰 사용확정·`PENDING`→`FUNDING_IN_PROGRESS` 전이가 일어나지 않는다.
- **재입고 알림 신청 기능 중복**: 위 7번 엔드포인트 참고 — member-service `MvpImplementationSummary.md`의 MEMBER-008과 소유권 정리 필요.
- **LIVE 쿠폰 "방송 중" 검증**: claim 시 live-service 조회를 생략 중.
- **`GET /api/v1/inventories/{rewardId}` 인증**: 게이트웨이 미라우팅으로 외부 차단만 하고, 서비스 간 호출에는 내부 키를 요구하지 않는다. project-service HTTP 클라이언트도 아직 Noop.
- **내부 펀딩 스냅샷 필드**: payment-service HTTP 클라이언트가 기대하는 `status`/`finalAmount`/`orderName`/`sellerId`/`couponIssuanceId`는 현재 응답에 없다.
- **`project.funding-deadline-reached.v1` 미발행**: ORDER-006 리스너는 있으나 project-service가 토픽을 발행하지 않아 성립/미달 판정이 트리거되지 않는다.

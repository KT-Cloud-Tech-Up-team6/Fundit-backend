## 인증

> `Auth Required` 엔드포인트는 게이트웨이(`platform:gateway-service`)가 JWT를 검증해 주입한 `X-User-Id`(`AuthHeaders.USER_ID`)로 사용자를 식별합니다. 서비스는 이 헤더를 직접 파싱하지 않고 `@LoginUser CurrentUser`로 주입받습니다. 게이트웨이를 우회한 직접 호출은 `X-Internal-Api-Key`(`AuthHeaders.INTERNAL_API_KEY`)가 없어 401로 차단됩니다. 웹훅(`POST /api/v1/payments/webhook/toss`)만 로그인 인증을 요구하지 않습니다.

> **식별자 계약 (cross-service ID 통일 #69)**: `fundingId`의 정본은 order-service `orderId`(`fundings.public_id`, UUID)다.
> - **v2** `POST /api/v2/payments`, `POST /api/v2/payments/confirm`, `POST /api/v2/refunds/defect`, `POST /api/v2/refunds/return`, `POST /api/v2/refunds/exchange`, `POST /api/v2/refunds/shipping-delay`, `GET /api/v2/refunds`는 UUID `fundingId`를 그대로 받는다/돌려준다. 승인/목록 응답의 `fundingId`도 UUID(`PaymentConfirmResponseV2`/`RefundSummaryResponseV2`).
> - **v1**은 레거시 Long(order-service 내부 PK)을 받아 내부 API로 UUID로 해석한다. v1 승인/목록 응답의 Long `fundingId`는 항상 null — 값이 필요하면 v2를 쓴다.
> - v2 HTTP 연동은 `GET /internal/orders/{orderId}`를 쓰고, v1은 `GET /internal/fundings/{fundingId}`(Long PK)를 쓴다.

## 1. 결제

### 1-1. POST `/api/v1/payments` — 결제 시도 생성 (PAYMENT-001)

- **권한**: 구매자(본인 주문만)
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Request Body**

```json
{
  "fundingId": 1024
}
```

- **처리 절차**: ① `OrderFundingClient.fetch(fundingId)`로 스냅샷을 받는다. **기본은 `StubOrderFundingClient`**(고정값). `order.integration.funding-client.mode=http`이면 `GET /internal/fundings/{fundingId}`를 `X-Internal-Api-Key`로 호출하고 `{ memberId, sellerId, status, finalAmount, orderName, couponIssuanceIds }`를 기대한다. **order-service 실제 응답은 `{ projectId, memberId, fundingPublicId }`뿐이라 HTTP 모드를 켜도 결제 시도에 필요한 필드가 비어 있다.** ② 스냅샷 `memberId`가 `CurrentUser.id`와 다르면 `403 FORBIDDEN` ③ `status != 'PENDING'`이면 `409 FUNDING_NOT_PENDING` ④ 검증 통과 시 `Payment(PENDING)` 생성, `finalAmount`/`orderName`/`couponIssuanceIds`를 `payments`에 스냅샷

- **Response 201 Created**

```json
{
  "paymentId": "0198f2b1-2c3d-7a1e-9c4f-6a2b1e0d8f31",
  "pgOrderId": "fundit-3f8a91c2b7",
  "amount": 89000,
  "orderName": "세상에 없는 프라이팬 외 1건",
  "couponIssuanceIds": [5, 6]
}
```

- **복수 쿠폰**: 주문에 플랫폼+메이커 쿠폰이 같이 있으면 `payments.coupon_issuance_ids`와 `payment.completed.v1`/`refund.completed.v1`의 `couponIssuanceIds`에 최대 2개(플랫폼+메이커) 전부 담긴다. 결제 금액(`amount`)은 두 쿠폰 할인을 합산한 `finalAmount`를 쓴다. 사용확정/복원도 리스트에 담긴 쿠폰 전부에 적용된다.

> 프론트엔드는 이 응답값으로 결제위젯을 초기화·렌더링한다.
> 1. `tossPayments.widgets({ customerKey })` — `customerKey`는 백엔드가 발급하지 않고, 로그인 회원의 `member_id`(UUID)를 프론트가 그대로 사용한다(무작위·비유추 값 요건 충족, `PaymentERD.md` 1장 참고). 이 응답에는 포함하지 않는다.
> 2. `widgets.setAmount({ value: amount })`
> 3. `widgets.renderPaymentMethods()` — 결제수단 선택 UI
> 4. `widgets.renderAgreement()` — 약관 동의 UI
> 5. 결제하기 클릭 시 `widgets.requestPayment({ orderId: pgOrderId, orderName, successUrl, failUrl })`
>
> 결제창 호출·리다이렉트는 서버를 거치지 않는다.

- **주요 에러 코드**: `FUNDING_NOT_PENDING`(409), `NOT_FOUND`(404, funding 없음), `FORBIDDEN`(403, 본인 주문 아님), `DEPENDENCY_FAILURE`(503, order-service 내부 API 호출 실패/타임아웃)

---

### 1-2. POST `/api/v1/payments/confirm` — 결제 승인 처리 (PAYMENT-002)

- **권한**: 구매자
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Request Body** (토스 `successUrl` 리다이렉트로 받은 쿼리 파라미터를 그대로 전달)

```json
{
  "paymentKey": "5EnNZRJGvaBX7zk2LhLqK9Wgpp3RY",
  "orderId": "fundit-3f8a91c2b7",
  "amount": 89000
}
```

- **처리 절차**: 서버가 `orderId`로 결제 시도 조회 → 본인 소유 검증 → `amount`를 PAYMENT-001 시점 스냅샷값과 대조(불일치 시 승인 API 호출 없이 즉시 실패) → 토스 결제승인 API(`POST /v1/payments/confirm`)를 **위젯 시크릿 키**로 서버-투-서버 호출 → 성공 시 `pg_payment_key` 저장 후 `COMPLETED` → 같은 트랜잭션에서 `payment_event_outbox`에 `PaymentCompleted` 적재(order-service DB에 직접 쓰지 않음, PAYMENT-016 워커가 `payment.completed.v1`로 비동기 발행). Kafka 페이로드는 `{ eventId, fundingId, couponIssuanceIds }`이다(`paidAt`/`paymentId`는 브로커로 나가지 않음).

- **Response 200 OK**

```json
{
  "paymentId": "0198f2b1-2c3d-7a1e-9c4f-6a2b1e0d8f31",
  "fundingId": 1024,
  "status": "COMPLETED",
  "paymentMethod": "EASY_PAY",
  "easyPayProvider": "KAKAOPAY",
  "paidAt": "2026-09-08T14:23:11"
}
```

> **주의**: 이 응답에는 주문(Funding)의 상태가 포함되지 않는다. `Funding.status`를 `FUNDING_IN_PROGRESS`로 바꾸는 것은 order-service가 `payment.completed.v1`을 구독해 비동기로 처리하므로, 이 API가 200을 반환한 시점과 `GET /api/v1/orders/{orderId}`(order-service)에서 최신 상태가 보이는 시점 사이에 짧은 지연이 있을 수 있다. 프론트엔드는 이 API의 `status: "COMPLETED"` 자체를 결제 성공의 기준으로 삼아야 한다.

- **주요 에러 코드**: `PAYMENT_NOT_PENDING`(409), `PAYMENT_AMOUNT_MISMATCH`(422), `PAYMENT_EXPIRED`(410, 인증 후 10분 초과), `PG_CONFIRM_FAILED`(422, 토스 승인 API 실패 응답 — 이 경우 `Payment.status=FAILED`로 기록되고 `Funding.status`는 order-service 쪽에서 그대로 `PENDING` 유지되어 클라이언트는 1-1부터 재시도 가능)

> **레이스 컨디션 처리**: 토스 승인 자체는 성공했으나(위 응답은 정상 200 반환) 이후 `payment.completed.v1`을 받은 order-service가 "이미 `PAYMENT_EXPIRED`"라고 판단하는 극히 드문 경우, 이 API 응답은 이미 나간 뒤이므로 별도 에러 코드로 표현하지 않는다. 대신 order-service가 `payment.reconciliation-required.v1`을 발행하면 payment-service가 PAYMENT-017로 자동 전액취소한다(비동기 보상 트랜잭션, `PaymentFunctionalSpec.md` PAYMENT-002/017 참고). payment 쪽 리스너·전액취소 로직은 이미 구현돼 있고, order-service의 발행이 붙으면 동작한다.

---

### 1-3. POST `/api/v1/payments/webhook/toss` — 토스 웹훅 수신 (보조)

- **권한**: 시스템(토스페이먼츠 서버만 호출). `@LoginUser`를 요구하지 않는다. `X-User-Id`가 없는 요청은 `InternalGatewaySecretFilter`가 통과시킨다.
- **Request Header**: 서명 헤더는 검증하지 않는다.
- **Request Body**: 토스 웹훅 이벤트(`PAYMENT_STATUS_CHANGED`, `CANCEL_STATUS_CHANGED` 등) 원본 페이로드. 검증 대상은 `data.secret`(본문 필드)이다. 승인 시점에 저장해 둔 `payments.pg_secret`과 상수 시간 비교한다.
- **Response 200 OK**: 빈 본문. 이 서비스가 모르는 `paymentKey`는 존재 여부를 노출하지 않기 위해 **200으로 무시**한다(토스 재시도 폭주 방지).
- **비고**: 1-2의 승인 흐름을 대체하지 않는 보조 상태 통지 수신용. `data.secret` 누락·불일치 시 401로 즉시 거부하고 처리하지 않는다. 웹훅 수신 URL은 개발자센터에 별도 등록해야 한다. 가상계좌(무통장입금) 지원이 확정되면 `DEPOSIT_CALLBACK` 처리가 이 엔드포인트에서 필수 로직으로 추가되어야 한다(현재 미확정, `PaymentERD.md` 6장 참고)
- **주요 에러 코드**: `WEBHOOK_SIGNATURE_INVALID`(401, 본문 `secret` 누락·불일치)

---

## 2. 환불

### 2-1. GET `/api/v1/refunds` — 환불 신청/처리 통합 내역 조회 (PAYMENT-003)

- **권한**: 구매자(본인 내역만)
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Query Parameter**: `page`, `size`
- **Response 200 OK**

```json
{
  "content": [
    {
      "refundId": 501,
      "fundingId": 1024,
      "triggerType": "DEFECT",
      "status": "UNDER_REVIEW",
      "amount": 89000,
      "returnShippingFee": null,
      "requestedAt": "2026-09-01T10:00:00",
      "reasonDetail": "[DAMAGED] 배송 중 파손되어 도착했습니다.",
      "rejectedReason": null,
      "completedAt": null,
      "projectTitle": "세상에 없는 프라이팬",
      "lineItems": [
        { "rewardName": "얼리버드 패키지", "quantity": 1, "unitPrice": 89000 }
      ]
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1,
  "hasNext": false
}
```

- **V04**: `reasonDetail`/`rejectedReason`/`completedAt`은 `refund_requests` 테이블 값을 그대로 노출한다(반려 전이면 `rejectedReason`은 null, 미처리 건이면 `completedAt`은 null). `projectTitle`/`lineItems`는 order-service 내부 배치 API(`GET /internal/orders/order-summaries`)로 페이지 단위 1회 조회해 채우며, 조회 실패 시 둘 다 null(부가 정보, 목록 자체는 정상 응답).
- **`amount`는 실 환불 금액이다**(결제 원금이 아님). 완료된 건은 `payment_cancellations.cancel_amount` 합계를, 아직 취소가 실행되지 않은 건(신청 중·반려)은 `payments.amount`를 폴백으로 내려준다 — 즉시처리 유형은 전액 취소라 폴백값이 곧 실 환불액이다. 반품비 차감 부분취소(2-3)를 별도 컬럼 없이 반영하기 위한 설계이며, 목록 쿼리 성능이 문제되면 그때 비정규화한다.
- **`returnShippingFee`**는 `triggerType=RETURN_CHANGE_OF_MIND`일 때만 `5000`이고 그 외 유형은 null이다(반품비는 전 프로젝트 공통 고정액이라 조회 시 상수로 채운다).
- **v2(`GET /api/v2/refunds`)는 사유를 나눠 내려준다** — `reasonType`(사유 유형 enum 이름, 유형 없는 사유는 null)과 `reasonDetail`(구매자가 쓴 상세만, 태그 제거)이다. 저장은 `"[DAMAGED] 배송 중 파손되어..."` 한 문자열이지만 FE가 이 문자열을 파싱하지 않도록 응답에서 분리한다(`RefundReasonTag`). 교환 건은 사유에 따른 `additionalPaymentAmount`(구매자 귀책 5000, 그 외 0)도 채워진다. **v1 응답은 기존 계약 유지** — `reasonDetail`에 태그가 포함된 원문이 그대로 내려가고 `reasonType`/`additionalPaymentAmount` 필드가 없다.
- **필터**: `triggerType` 쿼리 파라미터로 유형별 필터가 가능하다 — 발송 후 반품은 `RETURN_CHANGE_OF_MIND`, 모금 중 참여 취소는 `SIMPLE_CHANGE_OF_MIND`로 구분된다.

---

### 2-1b. GET `/api/v1/refunds/seller` — 판매자 환불 목록 (PAYMENT-003 seller 변형)

- **권한**: 판매자(본인이 판매자인 DEFECT 신청만 대상)
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Query Parameter**: `page`, `size`
- **Response 200 OK**: 2-1과 동일한 응답 스키마.
- **비고**: 하자환불 신청(2-2) 시점에 `refund_requests.seller_id`를 미리 채워둔 뒤(order-service 재조회 없이 신청 시점의 `OrderFundingClient` 응답을 그대로 저장) `seller_id`로만 필터링한다 — 즉시처리 트리거(참여취소/미달자동)는 `seller_id`가 없어 애초에 대상이 아니다.

---

### 2-2. POST `/api/v1/refunds/defect` — 하자환불 신청 (PAYMENT-006)

- **권한**: 구매자
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Request Body**

```json
{
  "fundingId": 1024,
  "defectType": "DAMAGED",
  "reasonDetail": "배송 중 파손되어 도착했습니다.",
  "evidenceUrls": ["https://cdn.fundit.example.com/evidence/abc.jpg"]
}
```

- **Response 201 Created**

```json
{ "refundId": 501, "status": "REQUESTED" }
```

- **접수 조건(공통 가드)**: 발송 후 신청 3종(하자환불·반품·교환)은 **소유권 → 배송완료·수령 후 7일 → 중복 신청** 순으로 같은 가드를 통과한다(`PostShipmentRefundRequestService`). 배송 완료 전이면 `409 NOT_DELIVERED`, 배송 완료 후 7일이 지났으면 `409 RETURN_PERIOD_EXPIRED`, 같은 주문에 미처리 신청이 남아 있으면 `409 REFUND_ALREADY_REQUESTED`다. 기한 기준일은 `deliveredAt`(배송 완료)이다 — `receiptConfirmedAt`은 배송완료 +7일에 자동 확정되므로 그것을 기준으로 삼으면 실제 신청 기간이 14일로 늘어난다.
- `defectType`(필수): `DEFECTIVE` | `DAMAGED` | `WRONG_DELIVERY` | `DIFFERENT_FROM_DESCRIPTION` | `MISSING_COMPONENTS` | `OTHER` — 모두 판매자 귀책이라 승인 시 전액 취소이고, `OTHER`만 귀책이 불분명해 사전 계산(2-6)이 확정액을 내리지 않는다. 저장 시 `[DAMAGED] ...` 형태로 사유 유형을 태그로 앞에 붙인다(반품·교환과 동일, 별도 컬럼 없음).
- **주요 에러 코드**: `EVIDENCE_REQUIRED`(400), `NOT_DELIVERED`(409), `RETURN_PERIOD_EXPIRED`(409), `REFUND_ALREADY_REQUESTED`(409), `NOT_FOUND`(404, 완료된 결제 없음), `FORBIDDEN`(403, 본인 주문 아님)

---

### 2-2b. POST `/api/v2/refunds/return` — 발송 후 단순변심 반품 접수 (환불 정책 V.1.0)

- **권한**: 구매자
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Request Body**

```json
{
  "fundingId": "0a9f1b2c-3d4e-7a1b-9c2d-6e5f4a3b2c1d",
  "returnReason": "CHANGE_OF_MIND",
  "reasonDetail": "색상이 생각과 달라요",
  "evidenceUrls": []
}
```

- `returnReason`(필수): `CHANGE_OF_MIND` | `WRONG_OPTION` — 둘 다 구매자 귀책이라 반품비 부담 규칙이 같다.
- `reasonDetail`(선택, 500자 이하), `evidenceUrls`(**선택**) — 정책상 구매자 귀책이라 입증 자료를 요구하지 않는다(하자환불 2-2와 다른 점). 저장 시 `[CHANGE_OF_MIND] ...` 형태로 사유 유형을 태그로 앞에 붙인다(하자환불과 동일, 별도 컬럼 없음).

- **Response 201 Created**

```json
{
  "refundId": 1234,
  "status": "REQUESTED",
  "paymentAmount": 23000,
  "returnShippingFee": 5000,
  "estimatedRefundAmount": 18000
}
```

- **처리 절차**: 2-2와 같은 공통 가드(소유권 → 배송완료·수령 후 7일 → 중복 신청)를 통과한 뒤 `refund_requests(trigger_type='RETURN_CHANGE_OF_MIND', status='REQUESTED')`를 생성한다. 중복 신청은 응용 계층 검사와 **DB 부분 유니크 인덱스**(`uq_refund_requests_unresolved_post_shipment`, V8) 두 겹으로 막는다 — 검사와 INSERT 사이의 경합에서 진 쪽은 인덱스 위반이 같은 `REFUND_ALREADY_REQUESTED`(409)로 번역된다. 정책이 "**회수 후 환불**"이므로 **접수 시점에는 PG 취소를 하지 않는다** — 판매자가 회수를 확인하고 2-3(`PATCH /api/v1/refunds/{refundId}/decision`)으로 승인하면 반품 배송비를 뺀 금액이 부분취소된다. 승인 주체는 판매자이며 신규 승인 API는 없다.
- **비고**: 반품 배송비는 **전 프로젝트 공통 5,000원**(`ReturnPolicy.RETURN_SHIPPING_FEE`, 정책 확정 2026-09-23)이다. 프로젝트/리워드별 반품비 정책이 생기면 project-service 조회로 바꾼다 — 그때까지 설정값으로 빼지 않는다. 성립 후 **발송 전** 단순변심 취소는 정책상 불가라 별도 경로가 없다(발송 지연일 때만 2-4로 취소 가능).
- **주요 에러 코드**: `NOT_DELIVERED`(409), `RETURN_PERIOD_EXPIRED`(409), `REFUND_ALREADY_REQUESTED`(409), `RETURN_FEE_EXCEEDS_AMOUNT`(422, 결제액이 반품비 이하), `NOT_FOUND`(404), `FORBIDDEN`(403)

---

### 2-2c. POST `/api/v2/refunds/exchange` — 발송 후 교환 접수 (MVP 범위 제한)

- **권한/요청**: 2-2b와 동일한 공통 가드를 쓴다.
- **Request Body**

```json
{
  "fundingId": "0a9f1b2c-3d4e-7a1b-9c2d-6e5f4a3b2c1d",
  "exchangeReason": "WRONG_OPTION",
  "reasonDetail": "사이즈를 잘못 골랐어요",
  "evidenceUrls": []
}
```

- `exchangeReason`(**선택**, 미전송 시 `OTHER`): 구매자 귀책 `CHANGE_OF_MIND` | `WRONG_OPTION`, 판매자 귀책 `DEFECTIVE` | `DAMAGED` | `WRONG_DELIVERY` | `MISSING_COMPONENTS` | `DIFFERENT_FROM_DESCRIPTION`, 그리고 `OTHER`. 사유가 교환 배송비 부담 주체를 정한다 — 구매자 귀책만 5,000원을 별도 결제한다(환불 정책 V.1.0). FE가 사유를 보내도록 전환하는 동안 선택 필드로 두고, 전환이 끝나면 필수로 바꾼다.
- `reasonDetail`(선택, 500자 이하), `evidenceUrls`(**선택**) — 구매자 귀책 사유에는 증빙을 요구하지 않는다(하자환불 2-2와 다른 점). 저장 시 `[WRONG_OPTION] ...` 형태로 사유 유형을 태그로 앞에 붙인다.

- **Response 201 Created**

```json
{
  "refundId": 1234,
  "status": "REQUESTED",
  "exchangeShippingFee": 5000,
  "additionalPaymentAmount": 5000
}
```

- `additionalPaymentAmount`는 구매자가 실제로 내야 하는 금액이다 — 판매자 귀책·기타는 `0`이다. FE가 교환비를 직접 계산하지 않도록 반품 접수(2-2b)와 대칭으로 내려준다.
- **범위 밖**: 교환 배송비 5,000원의 **실제 수납**과 재발송 연동(신규 결제 생성 + fulfillment 트리거)은 이번 범위가 아니다. 수납 방식은 **판매자 승인 시점에 토스 결제위젯 신규 결제**로 정해졌고(사유별 귀책이 승인에서 확정되므로 접수 시점에 받지 않는다), 구현은 교환 승인 흐름과 함께 한다 — 같은 펀딩의 두 번째 완료 결제를 막는 `uq_payments_completed_funding`을 `payments.purpose`(`REWARD`/`EXCHANGE_FEE`) 기준으로 바꾸는 작업이 선행돼야 한다. 지금 교환은 **접수·조회와 금액 안내까지만** 동작하며 2-3 승인 경로로 완료되지 않는다(`RefundTriggerType.EXCHANGE.toOrderServiceReason()`이 예외를 던진다).

---

### 2-3. PATCH `/api/v1/refunds/{refundId}/decision` — 발송 후 환불 신청 검토/승인/반려 (PAYMENT-007)

- **권한**: 판매자(해당 주문의 판매자 본인만)
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Request Body**

```json
{
  "decision": "APPROVED",
  "reason": null
}
```

반려 시:

```json
{
  "decision": "REJECTED",
  "reason": "제품 이상 없음 확인됨"
}
```

- **Response 200 OK**

```json
{ "refundId": 501, "status": "COMPLETED" }
```

- **대상**: 판매자 검토가 필요한 유형만이다 — 하자환불(`DEFECT`)과 구매자 귀책 반품(`RETURN_CHANGE_OF_MIND`). 그 외 유형은 `400 INVALID_INPUT`("판매자 검토 대상 신청이 아닙니다.")로 거부한다. 교환은 결제취소를 수반하지 않아 이 경로로 완료되지 않는다.
- **처리 절차**: 승인 시 같은 요청에서 `pg_payment_key` 기준 토스 취소 API를 호출하고, 성공하면 즉시 `COMPLETED`를 반환한다(`PROCESSING` 중간 상태를 응답하지 않음). **취소 금액은 귀책에 따라 다르다**(환불 정책 V.1.0):
  - 판매자 귀책(`DEFECT`) → `payments.amount` **전액 취소**, `is_full_refund=true`
  - 구매자 귀책 반품(`RETURN_CHANGE_OF_MIND`) → `payments.amount - 5000`(반품 배송비 차감) **부분취소**, `is_full_refund=false`. 결제 상태는 `COMPLETED`를 유지하고(전액취소 아님) 에스크로 보류도 유지된다.

  완료 시 `payment_event_outbox`에 `RefundCompleted` 적재 → Kafka `refund.completed.v1` 페이로드 `{ eventId, fundingId, couponIssuanceIds, refundReason, fullRefund }`. `refundReason`은 `DEFECT`면 `POST_SUCCESS_DEFECT`(`fullRefund=true`), 반품이면 `POST_SUCCESS_RETURN`(`fullRefund=false`)이다. 같은 트랜잭션에서 `notification.raised.v1`(notifType=`REFUND_STATUS`)도 적재한다. 반려 시에는 `RefundCompleted`를 발행하지 않고, 환불 상태 알림(`REJECTED`)만 발행한다.
- **주요 에러 코드**: `FORBIDDEN`(403, 타 판매자), `REASON_REQUIRED`(400, 반려인데 사유 없음), `INVALID_INPUT`(400, 판매자 검토 대상 유형 아님), `PG_CANCEL_FAILED`(422), `NOT_FOUND`(404)

---

### 2-4. POST `/api/v1/refunds/shipping-delay` — 발송지연 결제취소 신청 (PAYMENT-008)

- **권한**: 구매자
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Request Body**

```json
{ "fundingId": 1024 }
```

- **Response 201 Created**

```json
{ "refundId": 502, "status": "COMPLETED" }
```

- **비고**: fulfillment-service 내부 API(`GET /internal/fundings/{fundingId}/fulfillment-status`, 헤더 `X-Internal-Api-Key`) 응답의 `isAlreadyShipped`/`isDelayed`를 함께 확인한다(`FulfillmentDelayed` 이벤트는 구독하지 않고 동기 조회 결과만 씀). 이미 발송이면 `409 ALREADY_SHIPPED`. 미발송이어도 아직 발송 예정일이 지나지 않았으면(`isDelayed=false`) `422 NOT_YET_DELAYED`. 미발송이고 지연된 상태면 UNDER_REVIEW 단계 없이 즉시 전액 취소 → `refund.completed.v1`(`refundReason`=`POST_SUCCESS_DELAY`, `fullRefund`=`true`) 및 `notification.raised.v1` 적재.
- **주요 에러 코드**: `ALREADY_SHIPPED`(409, 이미 발송 시작됨), `NOT_YET_DELAYED`(422, 아직 발송 지연 상태 아님), `NOT_FOUND`(404), `FORBIDDEN`(403)

---

### 2-5. POST `/api/v1/refunds/evidence/upload-url` — 반품·교환 증빙 업로드 주소 발급 (F09)

- **권한**: 구매자(해당 orderId의 소유자만)
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Request Body**

```json
{ "orderId": "018f9a1b-....", "fileName": "evidence.jpg", "contentType": "image/jpeg", "fileSize": 204800 }
```

- **Response 200 OK**

```json
{ "uploadUrl": "https://fundit-media.s3.ap-northeast-2.amazonaws.com/refunds/....?X-Amz-...", "fileUrl": "https://fundit-media.s3.ap-northeast-2.amazonaws.com/refunds/018f9a1b-..../<uuid>.jpg" }
```

- **비고**: project-service `POST /api/v1/projects/{projectId}/media/upload-url`은 프로젝트 소유자(판매자)만 쓸 수 있어 구매자는 접근할 수 없다 — 이 엔드포인트가 구매자용 별도 경로다. `orderId`가 실제 이 회원의 것인지는 order-service `OrderFundingClient.fetch(orderId).memberId()`로 서버에서 검증한다(경로/바디의 식별자만으로 신뢰하지 않음, S4). S3 키는 `refunds/{orderId}/{uuid}.{ext}` 네임스페이스를 쓴다(project-service의 `projects/{projectId}/...`와 같은 버킷, 다른 프리픽스). 파일 바이트는 이 서버를 거치지 않고 클라이언트가 `uploadUrl`로 S3에 직접 PUT한다. 이미지만 허용(jpg/jpeg/png/webp, 10MB) — 업로드 완료 여부 확인(HeadObject)은 이번 범위에서 하지 않는다(신청 시 `evidenceUrls` 비어있으면 `EVIDENCE_REQUIRED`로만 막는다).
- **주요 에러 코드**: `FORBIDDEN`(403, 본인 주문 아님), `UNSUPPORTED_MEDIA_TYPE`(400), `MEDIA_TOO_LARGE`(400), `NOT_FOUND`(404, 존재하지 않는 orderId)

---

### 2-6. GET `/api/v1/refunds/estimate` — 환불·교환 금액 사전 계산 (R05)

- **권한**: 구매자(해당 orderId의 완료 결제 소유자만)
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Query**: `orderId`(UUID, order-service `fundings.public_id`), `triggerType`(선택, 신청하려는 유형), `defectType`(선택, 하자 사유), `exchangeReason`(선택, 교환 사유)
- **Response 200 OK**

```json
{
  "orderId": "018f9a1b-....",
  "paymentAmount": 23000,
  "rewardAmount": 20000,
  "shippingFee": 3000,
  "discountAmount": 0,
  "returnShippingFee": 5000,
  "additionalPaymentAmount": 0,
  "refundAmount": 18000,
  "confirmed": true
}
```

- **금액 산정**(환불 정책 V.1.0) — 화면은 `paymentAmount`·`returnShippingFee`·`additionalPaymentAmount`·`refundAmount`를 그대로 표기하고 직접 계산하지 않는다:

| `triggerType` | 사유 | `refundAmount` | `returnShippingFee` | `additionalPaymentAmount` | `confirmed` | 화면 표기 |
|---|---|---|---|---|---|---|
| 미지정 | – | `amount` | 0 | 0 | `true` | 예상 환불액 23,000원 |
| `RETURN_CHANGE_OF_MIND` | 단순변심·옵션오류 | `amount - 5000` | 5000 | 0 | `true` | 23,000 − 5,000 → 18,000원 |
| `SHIPPING_DELAY`·즉시처리 | – | `amount` | 0 | 0 | `true` | 예상 환불액 23,000원 |
| `DEFECT` | `OTHER` 제외 | `amount` | 0 | 0 | `false` | 23,000원(확인 시) |
| `DEFECT` | `OTHER` | `null` | 0 | 0 | `false` | 접수 후 확인하여 안내 |
| `EXCHANGE` | 구매자 귀책 2종 | `null` | 0 | 5000 | `true` | 교환 배송비 5,000원 → 추가 결제 5,000원 |
| `EXCHANGE` | 판매자 귀책 5종 | `null` | 0 | 0 | `false` | 추가 결제 0원(확인 시) |
| `EXCHANGE` | `OTHER`·미지정 | `null` | 0 | 0 | `false` | 접수 후 확인하여 안내 |

- **`confirmed`**: 금액이 정책으로 확정되는 건만 `true`다. 하자환불 전체와 판매자 귀책·기타 교환은 판매자 검토로 귀책이 정해져 금액이 바뀔 수 있어 `false`이며(정책 표기 "(확인 시)"), 화면은 확정액으로 표기하면 안 된다. `refundAmount=null`은 확정액 자체가 없는 경우다(교환은 환불을 수반하지 않고, 귀책 불분명 건은 검토 후 정해진다).
- **비고**: 신청 단위는 펀딩(주문) 전체만 지원한다(개별 리워드 부분 환불은 미지원). `rewardAmount`는 `amount - shippingFee + discountAmount`로 역산한다. 반품 사유 2종(`CHANGE_OF_MIND`/`WRONG_OPTION`)은 금액 규칙이 같아 별도 파라미터를 받지 않는다. 적립금/현금 분리는 MVP에 적립금이 없어 두지 않았고, 플랫폼 수수료는 정산 쪽 차감이라 구매자 환불액에 넣지 않는다.
- **주요 에러 코드**: `NOT_FOUND`(404, 완료 결제 없음), `FORBIDDEN`(403, 본인 결제 아님)

---

## 3. 정산

### 3-0. GET `/api/v1/settlements` — 정산 목록 (PAYMENT-009 변형)

- **권한**: 판매자(본인 배치만)
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Query Parameter**: `page`, `size`
- **Response 200 OK**

```json
{
  "content": [
    { "settlementBatchId": 77, "batchType": "INTERIM", "status": "PENDING",
      "periodStart": "2026-09-01T00:00:00Z", "periodEnd": "2026-09-07T00:00:00Z", "totalAmount": 4711000 }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false
}
```

- **비고**: `settlementBatchId`만 확인해 3-1 상세로 이어가는 진입점(리워드/옵션별 집계는 담지 않음, persistence-convention.md §3).

### 3-1. GET `/api/v1/settlements/{settlementBatchId}` — 정산 내역서 조회 (PAYMENT-009)

- **권한**: 판매자(본인 정산 건만)
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Response 200 OK**

```json
{
  "settlementBatchId": 77,
  "batchType": "INTERIM",
  "status": "PENDING",
  "grossAmount": 5000000,
  "platformFeeAmount": 150000,
  "refundDeductionAmount": 89000,
  "couponDeductionAmount": 50000,
  "totalAmount": 4711000,
  "lineItems": [
    { "rewardId": 12, "rewardName": "얼리버드 세트", "optionName": "블랙", "quantity": 40, "amount": 3560000 }
  ]
}
```

- **비고**: `lineItems`는 order-service `funding_line_items`/`funding_line_item_options` 조회 결과를 집계해 합성. `totalAmount = max(0, gross - platformFee - refundDeduction - couponDeduction)`(선정산도 순액 100%, 70% 선지급 계수 없음).
- **주요 에러 코드**: `FORBIDDEN`(403), `NOT_FOUND`(404)

### 3-2. GET `/api/v1/settlements/{settlementBatchId}/download` — 정산 내역서 다운로드 (PAYMENT-010)

- **권한**: 판매자(본인 정산 건만)
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Response 200 OK**: `Content-Type: text/csv; charset=UTF-8`, `Content-Disposition: attachment; filename="settlement-{settlementBatchId}.csv"`. 본문은 3-1 상세와 동일한 데이터(배치 요약 1행 + 리워드/옵션별 라인아이템)를 CSV로 직렬화한 것.
- **비고**: PDF/엑셀 대신 CSV로 제공한다(신규 의존성 없이 "테스트용 정산 파일" 요구사항 충족). 접근 권한 검증은 3-1과 동일한 로직(`SettlementQueryService.getDetail`)을 재사용한다.
- **주요 에러 코드**: `FORBIDDEN`(403), `NOT_FOUND`(404)

### 3-3. POST `/api/v1/settlements/{settlementBatchId}/disputes` — 정산 이의 신청 (PAYMENT-011)

- **권한**: 판매자
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Request Body**

```json
{
  "reason": "정산 금액 산출 내역이 실제 판매 수량과 다릅니다.",
  "evidenceUrls": ["https://cdn.fundit.example.com/evidence/dispute1.pdf"]
}
```

- **Response 201 Created**: `{ "disputeId": 12, "status": "RECEIVED" }`
- **처리 절차**: 접수 시 대상 `settlement_batches.status = ON_HOLD`로 전환(지급 보류). 이의신청 기산일은 배치 유형별로 다르다 — 선정산(INTERIM)은 배치 생성일(`createdAt`), 최종정산(FINAL)은 배치에 속한 펀딩들의 실제 배송완료일(fulfillment-service `ShippingStatusClient` 조회, PAYMENT-008과 동일 포트) 중 가장 늦은 값 + 14일
- **주요 에러 코드**: `DISPUTE_PERIOD_EXPIRED`(409, 이의신청 가능 기간 경과), `FORBIDDEN`(403), `NOT_FOUND`(404), `DEPENDENCY_FAILURE`(503, 최종정산인데 배송완료일 확인 불가)

### 3-4. GET `/api/v1/settlements/disputes` — 이의신청 목록 (PAYMENT-011 변형)

- **권한**: 판매자(본인 접수 건만)
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Query Parameter**: `page`, `size`
- **Response 200 OK**

```json
{
  "content": [
    { "disputeId": 12, "settlementBatchId": 77, "reason": "정산 금액 산출 내역이 실제 판매 수량과 다릅니다.",
      "status": "RECEIVED", "requestedAt": "2026-09-10T09:00:00Z", "resolvedAt": null }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false
}
```

### 3-5. GET `/api/v1/settlements/disputes/{disputeId}` — 이의신청 상세 (PAYMENT-011 변형)

- **권한**: 판매자(본인 접수 건만, S4)
- **Request Header**: `X-User-Id` (`@LoginUser CurrentUser`)
- **Response 200 OK**: 3-4 목록 항목과 동일한 스키마.
- **주요 에러 코드**: `FORBIDDEN`(403, 본인 접수 건 아님), `NOT_FOUND`(404)

---

## 4. REST 엔드포인트가 없는 기능 (이벤트 구독 / 스케줄러)

| 기능 ID | 트리거 | 비고 |
|---|---|---|
| PAYMENT-004 | 이벤트 구독(`funding.cancelled-by-member.v1`) | 입력 `{ eventId, fundingId, projectId, memberId }`. 참여 취소 전액 환불 → `refund.completed.v1`(`refundReason`=`CANCELLED_BY_MEMBER`, `fullRefund`=`true`) |
| PAYMENT-005 | 이벤트 구독(`funding.goal-failed.v1`) | 입력 `{ eventId, fundingId, projectId }`. **펀딩 1건당 1이벤트**(프로젝트 단위 일괄 처리 아님) → `refund.completed.v1`(`refundReason`=`GOAL_FAILURE_AUTO_REFUND`, `fullRefund`=`true`) |
| PAYMENT-012 | 배치(정산 처리 시) | 쿠폰 정산 차감 계산(메이커 발급 쿠폰만) |
| PAYMENT-013 | 이벤트 구독(`funding.succeeded.v1`) + 스케줄 | 입력 `{ eventId, fundingId, projectId, sellerId, achievedAt }`. 달성확정일+5영업일 후 선정산 배치. `totalAmount`는 순액 100% |
| PAYMENT-014 | 이벤트 구독(`shipping.completed.v1`) + 스케줄 | 배송완료 +14일 후 최종정산 배치 생성 |
| PAYMENT-015 | 스케줄러(매주 금요일) | 정산 지급 실행(`PENDING`만, `ON_HOLD` 제외) |
| PAYMENT-016 | 내부 스케줄러(폴링) | `payment_event_outbox` 미발행 건을 `payment.completed.v1` / `refund.completed.v1`로 발행 |
| PAYMENT-017 | 이벤트 구독(`payment.reconciliation-required.v1`) | **리스너·전액취소 로직은 payment 쪽에 구현됨.** order-service가 아직 이 토픽을 발행하지 않아 실제 트래픽은 없음. 발행되면 `trigger_type=SYSTEM_RECONCILIATION`으로 전액취소 |

### 발행 이벤트 계약 (Kafka, 봉투 없이 평평한 JSON)

**`payment.completed.v1`** (파티션 키 `fundingId`)

```json
{
  "eventId": "payment:42",
  "fundingId": "0a9f1b2c-3d4e-7a1b-9c2d-6e5f4a3b2c1d",
  "couponIssuanceIds": [7, 8]
}
```

**`refund.completed.v1`** (파티션 키 `fundingId`)

```json
{
  "eventId": "payment:77",
  "fundingId": "0a9f1b2c-3d4e-7a1b-9c2d-6e5f4a3b2c1d",
  "couponIssuanceIds": [7],
  "refundReason": "CANCELLED_BY_MEMBER",
  "fullRefund": true
}
```

`refundReason`은 order-service `PaymentEventListener.RefundReason` 이름과 일치한다: `GOAL_FAILURE_AUTO_REFUND` / `CANCELLED_BY_MEMBER` / `POST_SUCCESS_DEFECT` / `POST_SUCCESS_DELAY` / `POST_SUCCESS_RETURN`. 필드명은 `refundReason`/`fullRefund`이다(`triggerType`/`isFullRefund`가 아님).

> ⚠️ **배포 순서: order-service(컨슈머) → payment-service(프로듀서).** `POST_SUCCESS_RETURN`은 새로 추가된 값이라, 이 값을 모르는 컨슈머가 먼저 받으면 역직렬화가 실패한다.
>
> `POST_SUCCESS_RETURN`은 반품비가 차감된 부분환불이라 `fullRefund=false`로 나가지만, order-service는 이 사유에서 쿠폰을 복원하지 않고(구매자 귀책) 주문 상태만 `REFUNDED_AFTER_SUCCESS`로 전이시킨다 — 하자·지연(`fullRefund=true`일 때만 전이)과 다른 점이다.

**`notification.raised.v1`** (파티션 키 `memberId`) — 환불 상태 알림. `RefundExecutionService`가 완료·대체계좌대기 시, `DefectRefundDecisionService`가 반려 시 아웃박스에 적재한다.

```json
{
  "eventId": "payment:88",
  "memberId": "0198f2b1-2c3d-7a1e-9c4f-6a2b1e0d8f31",
  "notifType": "REFUND_STATUS",
  "title": "환불이 완료되었어요",
  "relatedUrl": "/my/fundings/1024/refund"
}
```

`title`은 상태에 따라 `환불이 완료되었어요` / `환불 처리를 위해 계좌 정보가 필요해요` / `환불 신청이 반려되었어요`.

---

## 5. `PaymentErrorCode`

error-handling.md 컨벤션에 따라 `ErrorCode` 인터페이스를 구현하는 payment-service 전용 플랫 enum입니다. `CommonErrorCode`(NOT_FOUND, FORBIDDEN, INVALID_INPUT, DEPENDENCY_FAILURE, SERVICE_UNAVAILABLE 등)는 재사용하고, 아래는 도메인 전용 코드만 정리했습니다.

| 코드 | HTTP | 설명 |
|---|---|---|
| `FUNDING_NOT_PENDING` | 409 | 결제 시도 대상 주문이 PENDING 상태가 아님(order-service 내부 API 응답 기준) |
| `PAYMENT_NOT_PENDING` | 409 | 승인 대상 결제가 PENDING 상태가 아님 |
| `PAYMENT_AMOUNT_MISMATCH` | 422 | 승인 요청 금액과 서버 저장 금액 불일치(위변조 의심) |
| `PAYMENT_EXPIRED` | 410 | 토스 인증 후 10분 초과로 결제 만료 |
| `PG_CONFIRM_FAILED` | 422 | 토스 결제승인 API 실패 응답 |
| `PG_CANCEL_FAILED` | 422 | 토스 결제취소 API 실패 응답 |
| `WEBHOOK_SIGNATURE_INVALID` | 401 | 토스 웹훅 본문 `data.secret` 누락·불일치 |
| `EVIDENCE_REQUIRED` | 400 | 하자환불 신청 시 증빙 자료 누락(반품·교환은 증빙이 선택이라 해당 없음) |
| `REASON_REQUIRED` | 400 | 하자환불 반려 시 사유 누락 |
| `ALREADY_SHIPPED` | 409 | 발송지연 취소 신청 시점에 이미 발송 시작됨(`isAlreadyShipped=true`) |
| `NOT_YET_DELAYED` | 422 | 발송지연 취소 신청 시점에 아직 발송 예정일이 지나지 않음(`isDelayed=false`) |
| `NOT_DELIVERED` | 409 | 배송 완료 전에 반품·교환·하자환불을 신청함(`deliveredAt=null`) |
| `RETURN_PERIOD_EXPIRED` | 409 | 수령(배송 완료) 후 7일 경과 — FE "반품·교환 가능 기간이 지났어요"가 이 코드에 매핑된다 |
| `REFUND_ALREADY_REQUESTED` | 409 | 같은 주문에 진행 중인 반품·교환·하자환불 신청이 이미 있음(부분취소 중복 실행 방지) |
| `RETURN_FEE_EXCEEDS_AMOUNT` | 422 | 결제 금액이 반품 배송비(5,000원) 이하라 차감 후 환불액이 남지 않음 |
| `DISPUTE_PERIOD_EXPIRED` | 409 | 정산 이의신청 가능 기간(7일) 경과 |

> `CommonErrorCode.DEPENDENCY_FAILURE`(503)는 PAYMENT-001의 order-service 내부 API 호출 실패 시 재사용. `CommonErrorCode.NOT_FOUND`(404)는 funding/결제/환불/정산 대상을 찾지 못했을 때 재사용(`FUNDING_NOT_FOUND` 코드는 없음).

---

## 6. 남은 확인 필요 사항

- **`payment_event_outbox` 재시도 상한**: PAYMENT-016 예외처리의 "N회 이상 연속 실패 시 알림" N값 미정.
- **PAYMENT-017 `SYSTEM_RECONCILIATION` → order-service `RefundReason` 매핑**: payment는 `trigger_type=SYSTEM_RECONCILIATION`으로 기록하고, Kafka `refundReason`은 임시로 `GOAL_FAILURE_AUTO_REFUND`로 보낸다(order-service enum에 대응 값이 없음). order-service 발행·enum 추가 시 재확인.
- **order-service 내부 API 연동**: `GET /internal/fundings/{fundingId}`/`GET /internal/orders/{orderId}`가 `sellerId`/`status`/`finalAmount`/`orderName`/`couponIssuanceIds`를 전부 제공한다(연동 완료, CLAUDE.md "연동 현황" 참고). 기본 모드는 여전히 stub — 로컬에서 order-service를 안 띄웠으면 `ORDER_FUNDING_CLIENT_MODE=stub` 유지.

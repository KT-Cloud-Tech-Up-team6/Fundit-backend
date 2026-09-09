## 1. 결제

### 1-1. POST `/api/v1/payments` — 결제 시도 생성 (PAYMENT-001)

- **권한**: 구매자(본인 주문만)
- **Request Header**: `X-Account-Id`
- **Request Body**

```json
{
  "fundingId": 1024
}
```

- **처리 절차**: ① **[신규]** order-service 내부 API `GET /internal/fundings/{fundingId}` 동기 호출(서비스 간 인증 방식은 5장 하단 참고[정책 확인 필요]) → 응답으로 `memberId, status, finalAmount, orderName` 수신 ② `memberId`가 `X-Account-Id`와 다르면 `403 FORBIDDEN` ③ `status != 'PENDING'`이면 `409 FUNDING_NOT_PENDING` ④ 검증 통과 시 `Payment(PENDING)` 생성, `finalAmount`/`orderName`을 `payments.amount`/`payments.order_name`에 스냅샷

- **Response 201 Created**

```json
{
  "paymentId": "0198f2b1-2c3d-7a1e-9c4f-6a2b1e0d8f31",
  "pgOrderId": "fundit-3f8a91c2b7",
  "amount": 89000,
  "orderName": "세상에 없는 프라이팬 외 1건"
}
```

> 프론트엔드는 이 응답값으로 결제위젯을 초기화·렌더링한다.
> 1. `tossPayments.widgets({ customerKey })` — `customerKey`는 백엔드가 발급하지 않고, 로그인 회원의 `member_id`(UUID)를 프론트가 그대로 사용한다(무작위·비유추 값 요건 충족, `PaymentERD.md` 1장 참고). 이 응답에는 포함하지 않는다.
> 2. `widgets.setAmount({ value: amount })`
> 3. `widgets.renderPaymentMethods()` — 결제수단 선택 UI
> 4. `widgets.renderAgreement()` — 약관 동의 UI
> 5. 결제하기 클릭 시 `widgets.requestPayment({ orderId: pgOrderId, orderName, successUrl, failUrl })`
>
> 결제창 호출·리다이렉트는 서버를 거치지 않는다.

- **주요 에러 코드**: `FUNDING_NOT_PENDING`(409), `NOT_FOUND`(404, funding 없음), `FORBIDDEN`(403, 본인 주문 아님), **`DEPENDENCY_FAILURE`(503, 신규 — order-service 내부 API 호출 실패/타임아웃)**

---

### 1-2. POST `/api/v1/payments/confirm` — 결제 승인 처리 (PAYMENT-002)

- **권한**: 구매자
- **Request Header**: `X-Account-Id`
- **Request Body** (토스 `successUrl` 리다이렉트로 받은 쿼리 파라미터를 그대로 전달)

```json
{
  "paymentKey": "5EnNZRJGvaBX7zk2LhLqK9Wgpp3RY",
  "orderId": "fundit-3f8a91c2b7",
  "amount": 89000
}
```

- **처리 절차**: 서버가 `orderId`로 결제 시도 조회 → `amount`를 PAYMENT-001 시점 스냅샷값과 대조(불일치 시 승인 API 호출 없이 즉시 실패) → 토스 결제승인 API(`POST /v1/payments/confirm`)를 **위젯 시크릿 키**로 서버-투-서버 호출 → 성공 시 `pg_payment_key` 저장 후 `COMPLETED` → **[신규]** 같은 트랜잭션에서 `payment_event_outbox`에 `PaymentCompleted` 적재(order-service DB에 직접 쓰지 않음, PAYMENT-016 워커가 비동기 발행)

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

> **[신규] 주의**: 이 응답에는 주문(Funding)의 상태가 포함되지 않는다. `Funding.status`를 `FUNDING_IN_PROGRESS`로 바꾸는 것은 order-service가 `PaymentCompleted` 이벤트를 구독해 비동기로 처리하므로, 이 API가 200을 반환한 시점과 `GET /api/v1/orders/{orderId}`(order-service)에서 최신 상태가 보이는 시점 사이에 짧은 지연이 있을 수 있다. 프론트엔드는 이 API의 `status: "COMPLETED"` 자체를 결제 성공의 기준으로 삼아야 한다.

- **주요 에러 코드**: `PAYMENT_NOT_PENDING`(409), `PAYMENT_AMOUNT_MISMATCH`(422), `PAYMENT_EXPIRED`(410, 인증 후 10분 초과), `PG_CONFIRM_FAILED`(422, 토스 승인 API 실패 응답 — 이 경우 `Payment.status=FAILED`로 기록되고 `Funding.status`는 order-service 쪽에서 그대로 `PENDING` 유지되어 클라이언트는 1-1부터 재시도 가능)

> **[신규] 레이스 컨디션 처리**: 토스 승인 자체는 성공했으나(위 응답은 정상 200 반환) 이후 `PaymentCompleted` 이벤트를 받은 order-service가 "이미 `PAYMENT_EXPIRED`"라고 판단하는 극히 드문 경우, 이 API 응답은 이미 나간 뒤이므로 별도 에러 코드로 표현하지 않는다. 대신 order-service가 `PaymentReconciliationRequired`를 발행하고 payment-service가 PAYMENT-017로 자동 전액취소한다(비동기 보상 트랜잭션, `PaymentFunctionalSpec.md` PAYMENT-002/017 참고).

---

### 1-3. POST `/api/v1/payments/webhook/toss` — 토스 웹훅 수신 (보조)

- **권한**: 시스템(토스페이먼츠 서버만 호출, 서명 검증 필수)
- **Request Header**: 토스 웹훅 서명 헤더(수신 시 반드시 검증, S7)
- **Request Body**: 토스 웹훅 이벤트(`PAYMENT_STATUS_CHANGED`, `CANCEL_STATUS_CHANGED` 등) 원본 페이로드
- **Response 200 OK**: 빈 본문
- **비고**: 1-2의 승인 흐름을 대체하지 않는 보조 상태 통지 수신용. 서명 검증 실패 시 401로 즉시 거부하고 처리하지 않는다. 웹훅 수신 URL은 개발자센터에 별도 등록해야 한다. 가상계좌(무통장입금) 지원이 확정되면 `DEPOSIT_CALLBACK` 처리가 이 엔드포인트에서 필수 로직으로 추가되어야 한다(현재 미확정, `PaymentERD.md` 6장 참고)
- **주요 에러 코드**: `WEBHOOK_SIGNATURE_INVALID`(401)

---

## 2. 환불

### 2-1. GET `/api/v1/refunds` — 환불 신청/처리 통합 내역 조회 (PAYMENT-003)

*(변경 없음)*

- **권한**: 구매자(본인 내역만)
- **Request Header**: `X-Account-Id`
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
      "requestedAt": "2026-09-01T10:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1,
  "hasNext": false
}
```

---

### 2-2. POST `/api/v1/refunds/defect` — 하자환불 신청 (PAYMENT-006)

*(변경 없음)*

- **권한**: 구매자
- **Request Header**: `X-Account-Id`
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

- **주요 에러 코드**: `EVIDENCE_REQUIRED`(400), `FUNDING_NOT_FOUND`(404)

---

### 2-3. PATCH `/api/v1/refunds/{refundId}/decision` — 하자환불 검토/승인/반려 (PAYMENT-007)

- **권한**: 판매자(해당 주문의 판매자 본인만)
- **Request Header**: `X-Account-Id`
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
{ "refundId": 501, "status": "PROCESSING" }
```

- **처리 절차**: 승인 시 `pg_payment_key` 기준 토스 취소 API 호출 → `payment_cancellations` 기록 → `refund_requests.is_full_refund` 판정(취소금액=`payments.amount`면 `true`, 반품비 차감 등으로 적으면 `false`) → 완료 시 `COMPLETED` → **[신규]** `payment_event_outbox`에 `RefundCompleted`(payload:{triggerType:'DEFECT', isFullRefund}) 적재. 반려 시에는 이벤트를 발행하지 않는다.
- **주요 에러 코드**: `FORBIDDEN`(403, 타 판매자), `REASON_REQUIRED`(400, 반려인데 사유 없음), `PG_CANCEL_FAILED`(422)

---

### 2-4. POST `/api/v1/refunds/shipping-delay` — 발송지연 결제취소 신청 (PAYMENT-008)

- **권한**: 구매자
- **Request Header**: `X-Account-Id`
- **Request Body**

```json
{ "fundingId": 1024 }
```

- **Response 201 Created**

```json
{ "refundId": 502, "status": "COMPLETED" }
```

- **비고**: FS-096 판정 결과(`FulfillmentDelayed`) 확인 후 접수, 승인 시 즉시 전액 취소(단순변심·미달자동과 동일하게 UNDER_REVIEW 단계 없음) → **[신규]** 완료와 동시에 `payment_event_outbox`에 `RefundCompleted`(payload:{triggerType:'SHIPPING_DELAY', isFullRefund:true}) 적재
- **주요 에러 코드**: `ALREADY_SHIPPED`(409, 이미 발송 시작됨)

---

## 3. 정산

### 3-1. GET `/api/v1/settlements/{settlementBatchId}` — 정산 내역서 조회 (PAYMENT-009)

*(변경 없음)*

- **권한**: 판매자(본인 정산 건만)
- **Request Header**: `X-Account-Id`
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
  "totalAmount": 3298700,
  "lineItems": [
    { "rewardId": 12, "rewardName": "얼리버드 세트", "optionName": "블랙", "quantity": 40, "amount": 3560000 }
  ]
}
```

- **비고**: `lineItems`는 order-service `funding_line_items`/`funding_line_item_options` 조회 결과를 집계해 합성
- **주요 에러 코드**: `FORBIDDEN`(403), `NOT_FOUND`(404)

### 3-2. GET `/api/v1/settlements/{settlementBatchId}/download` — 정산 내역서 다운로드 (PAYMENT-010)

- **권한**: 판매자(본인 정산 건만)
- **Response 200 OK**: `{ "downloadUrl": "https://.../settlement-77.pdf" }`

### 3-3. POST `/api/v1/settlements/{settlementBatchId}/disputes` — 정산 이의 신청 (PAYMENT-011)

- **권한**: 판매자
- **Request Body**

```json
{
  "reason": "정산 금액 산출 내역이 실제 판매 수량과 다릅니다.",
  "evidenceUrls": ["https://cdn.fundit.example.com/evidence/dispute1.pdf"]
}
```

- **Response 201 Created**: `{ "disputeId": 12, "status": "RECEIVED" }`
- **처리 절차**: 접수 시 대상 `settlement_batches.status = ON_HOLD`로 전환(지급 보류)
- **주요 에러 코드**: `DISPUTE_PERIOD_EXPIRED`(409, 발송일로부터 7일 경과)

---

## 4. REST 엔드포인트가 없는 기능 (이벤트 구독 / 스케줄러)

| 기능 ID | 트리거 | 비고 |
|---|---|---|
| PAYMENT-004 | 이벤트 구독(`FundingCancelledByMember`) | 참여 취소 환불 실행, `pg_payment_key` 기준 토스 취소 API 호출 → `RefundCompleted` 발행 |
| PAYMENT-005 | 이벤트 구독(`FundingGoalFailed`) | 미달 자동환불 일괄 실행 → 건별 `RefundCompleted` 발행 |
| PAYMENT-012 | 배치(정산 처리 시) | 쿠폰 정산 차감 계산 |
| PAYMENT-013 | 이벤트 구독(`FundingSucceeded`) + 5영업일 스케줄 | 선정산 배치 생성 |
| PAYMENT-014 | 스케줄러(배송완료 +14일) | 최종정산 배치 생성 |
| PAYMENT-015 | 스케줄러(매주 금요일) | 정산 지급 실행 |
| **PAYMENT-016** `신규` | 내부 스케줄러(폴링) | `payment_event_outbox` 미발행 건을 브로커로 발행(order-service `funding_event_outbox` 워커와 동일 패턴) |
| **PAYMENT-017** `신규` | 이벤트 구독(`PaymentReconciliationRequired`, order-service가 발행) | 결제 성공-재고만료 레이스 컨디션 발생 시 자동 전액취소 |

---

## 5. `PaymentErrorCode` (초안)

error-handling.md 컨벤션에 따라 `ErrorCode` 인터페이스를 구현하는 payment-service 전용 플랫 enum입니다. `CommonErrorCode`(NOT_FOUND, FORBIDDEN, INVALID_INPUT, DEPENDENCY_FAILURE 등)는 재사용하고, 아래는 도메인 전용 코드만 정리했습니다.

| 코드 | HTTP | 설명 |
|---|---|---|
| `FUNDING_NOT_PENDING` | 409 | 결제 시도 대상 주문이 PENDING 상태가 아님(order-service 내부 API 응답 기준) |
| `PAYMENT_NOT_PENDING` | 409 | 승인 대상 결제가 PENDING 상태가 아님 |
| `PAYMENT_AMOUNT_MISMATCH` | 422 | 승인 요청 금액과 서버 저장 금액 불일치(위변조 의심) |
| `PAYMENT_EXPIRED` | 410 | 토스 인증 후 10분 초과로 결제 만료 |
| `PG_CONFIRM_FAILED` | 422 | 토스 결제승인 API 실패 응답 |
| `PG_CANCEL_FAILED` | 422 | 토스 결제취소 API 실패 응답 |
| `WEBHOOK_SIGNATURE_INVALID` | 401 | 토스 웹훅 서명 검증 실패 |
| `EVIDENCE_REQUIRED` | 400 | 하자환불 신청 시 증빙 자료 누락 |
| `REASON_REQUIRED` | 400 | 하자환불 반려 시 사유 누락 |
| `ALREADY_SHIPPED` | 409 | 발송지연 취소 신청 시점에 이미 발송 시작됨 |
| `DISPUTE_PERIOD_EXPIRED` | 409 | 정산 이의신청 가능 기간(7일) 경과 |

> `CommonErrorCode.DEPENDENCY_FAILURE`(503)는 PAYMENT-001의 order-service 내부 API 호출 실패 시 재사용(신규 코드 추가하지 않음).

---

## 6. ⚠️ 남은 확인 필요 사항 (이번 개정에서 신규 발견)

- **order-service 내부 API 인증 방식**: `GET /internal/fundings/{fundingId}` 호출에 쓸 서비스 간 인증이 아직 팀 컨벤션에 없음. `X-Account-Id`는 최종 사용자 식별용이라 그대로 못 씀 — 내부 전용 네트워크 경로 + 서비스 토큰 방식 제안, 확정 필요[정책 확인 필요].
- **`payment_event_outbox` 재시도 상한**: PAYMENT-016 예외처리의 "N회 이상 연속 실패 시 알림" N값 미정.
- **PAYMENT-017의 `refund_requests.trigger_type` 신규값**: 기존 4개 값(`SIMPLE_CHANGE_OF_MIND`/`GOAL_FAILED_AUTO`/`DEFECT`/`SHIPPING_DELAY`)에 레이스 컨디션 보상용 값을 추가할지, 기존 값 중 하나를 재사용할지 결정 필요.

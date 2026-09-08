# payment-service

> 루트 `CLAUDE.md`(레포 공통 규칙)와 `.claude/rules/`를 전제로, 여기는 payment-service에만 해당하는 내용만 다룹니다.
> 아직 `services/payment-service` 폴더가 없는 상태에서 작성되었습니다 — `settings.gradle`에는 이미 이름이 등록돼 있어(`payment-service` 폴더가 생기면 자동으로 `include`됨) 폴더/`build.gradle`만 만들면 바로 빌드에 잡힙니다.
> 이 문서는 `PaymentFunctionalSpec.md`(PAYMENT-001~017, API 9개+웹훅 1개=10개 / 이벤트·스케줄러 8개)를 **전부** 구현 대상으로 다룹니다. 아래 "전체 구현 대상 체크리스트"가 이 문서의 핵심입니다.

## 이 서비스가 하는 일

토스페이먼츠 결제위젯 연동을 통한 결제 시도/승인, 환불(참여취소/미달자동/하자/발송지연), 정산(선정산/최종정산/지급)을 소유합니다.

리워드/재고/쿠폰/펀딩(주문) 상태는 order-service 소관입니다. 이 서비스는 결제·환불·정산 실행만 담당하고, `fundings.status`나 `coupon_issuances`처럼 order-service 소유 테이블은 **절대 직접 쓰지 않습니다** — order-service가 이미 이 서비스가 발행할 이벤트를 구독하는 코드까지 만들어둔 상태입니다(아래 "⚠️ 가장 먼저 읽을 것" 참고).

---

## 전체 구현 대상 체크리스트 (API 10개 + 이벤트/스케줄러 8개 = 총 18개)

`PaymentFunctionalSpec.md`의 PAYMENT-001~017은 REST API로 노출되는 것과 이벤트 구독/스케줄러로만 동작하는 것이 섞여 있습니다. 아래 표가 전체 구현 대상입니다 — 이 표에 없는 기능은 이 서비스 범위가 아닙니다.

### API (10개)

| # | 기능ID | Method & Path | 설명 |
|---|---|---|---|
| 1 | PAYMENT-001 | `POST /api/v1/payments` | 결제 시도 생성(결제위젯 렌더링 준비) |
| 2 | PAYMENT-002 | `POST /api/v1/payments/confirm` | 결제 승인 처리 |
| 3 | — | `POST /api/v1/payments/webhook/toss` | 토스 웹훅 수신(기능ID 없는 보조 엔드포인트, 서명검증만) |
| 4 | PAYMENT-003 | `GET /api/v1/refunds` | 환불 신청/처리 통합 내역 조회 |
| 5 | PAYMENT-006 | `POST /api/v1/refunds/defect` | 하자환불 신청 |
| 6 | PAYMENT-007 | `PATCH /api/v1/refunds/{refundId}/decision` | 하자환불 검토/승인/반려 |
| 7 | PAYMENT-008 | `POST /api/v1/refunds/shipping-delay` | 발송지연 결제취소 신청 |
| 8 | PAYMENT-009 | `GET /api/v1/settlements/{settlementBatchId}` | 정산 내역서 조회 |
| 9 | PAYMENT-010 | `GET /api/v1/settlements/{settlementBatchId}/download` | 정산 내역서 다운로드 |
| 10 | PAYMENT-011 | `POST /api/v1/settlements/{settlementBatchId}/disputes` | 정산 이의 신청 |

### 이벤트 구독 / 스케줄러 (8개, REST 엔드포인트 없음)

| # | 기능ID | 트리거 | 설명 |
|---|---|---|---|
| 1 | PAYMENT-004 | 이벤트 구독(`FundingCancelledByMember`) | 참여 취소(단순변심) 환불 실행 |
| 2 | PAYMENT-005 | 이벤트 구독(`FundingGoalFailed`) | 미달 자동환불 실행 |
| 3 | PAYMENT-012 | 배치(정산 처리 시 내부 호출) | 쿠폰 정산 차감 계산 |
| 4 | PAYMENT-013 | 이벤트 구독(`FundingSucceeded`) + 5영업일 스케줄 | 선정산(INTERIM) 배치 생성 |
| 5 | PAYMENT-014 | 스케줄러(배송완료 +14일) | 최종정산(FINAL) 배치 생성 |
| 6 | PAYMENT-015 | 스케줄러(매주 금요일) | 정산 지급 실행 |
| 7 | PAYMENT-016 | 내부 스케줄러(폴링) | 결제/환불 완료 이벤트 아웃박스 발행 워커 |
| 8 | PAYMENT-017 | 이벤트 구독(`PaymentReconciliationRequired`) | 결제-재고만료 충돌 자동 환불 |

이 18개를 다 구현하기 전까지는 "완료"로 보지 마세요. 특히 PAYMENT-003/006/009~011처럼 앞선 대화에서 자세히 안 다뤄진 API도 아래 "API별 구현 노트"에 전부 있습니다.

---

## ⚠️ 가장 먼저 읽을 것 — order-service가 이미 이 서비스를 기다리고 있습니다

order-service는 이미 구현되어 있고, **이 서비스가 발행할 이벤트를 구독하는 포트/서비스까지 미리 만들어놨습니다**(`services/order-service/src/main/java/com/fundit/order/application/payment/PaymentEventListener.java`, `PaymentEventSyncService.java`). 즉 이벤트 payload 형태가 이미 "계약"으로 고정돼 있고, **이 서비스는 그 계약에 맞춰 발행해야 합니다.**

```java
// order-service PaymentEventListener 인터페이스 (이미 존재, 실제 소스 그대로)
public interface PaymentEventListener {
    void onPaymentCompleted(PaymentCompletedEvent event);
    void onRefundCompleted(RefundCompletedEvent event);

    record PaymentCompletedEvent(Long fundingId, Long couponIssuanceId) {}

    enum RefundReason {
        GOAL_FAILURE_AUTO_REFUND, CANCELLED_BY_MEMBER, POST_SUCCESS_DEFECT, POST_SUCCESS_DELAY
    }
    record RefundCompletedEvent(Long fundingId, Long couponIssuanceId, RefundReason refundReason, boolean fullRefund) {}
}
```

**이 서비스가 반드시 지켜야 할 것:**
- `PaymentCompleted` 이벤트에는 **`couponIssuanceId`를 이 서비스가 직접 채워서 보내야 합니다.** 이 값은 order-service의 `funding_coupon_applications`에만 있는 값이라, 이 서비스가 결제 시도 생성 시점(PAYMENT-001)에 order-service로부터 함께 받아서 `payments` 테이블에 같이 저장해뒀다가, 이벤트 발행 시 그대로 실어 보내야 합니다. 쿠폰이 적용 안 된 주문이면 `null`.
- `RefundCompleted`의 `refundReason`은 order-service가 이미 정의한 enum 4종 이름 그대로 매핑해서 보내야 합니다:

  | 이 서비스의 환불 트리거(`refund_requests.trigger_type`) | order-service가 기대하는 `RefundReason` | 비고 |
  |---|---|---|
  | 미달 자동환불 | `GOAL_FAILURE_AUTO_REFUND` | 쿠폰 항상 복원 |
  | 참여 취소(단순변심) | `CANCELLED_BY_MEMBER` | order-service가 사실상 무시(ORDER-014에서 이미 동기로 처리)하지만 이벤트는 그대로 발행 |
  | 하자환불 | `POST_SUCCESS_DEFECT` | 전액환불일 때만 쿠폰 복원 |
  | 발송지연 취소 | `POST_SUCCESS_DELAY` | 전액환불일 때만 쿠폰 복원 |

- `fundingId`는 `Long`(내부 PK)입니다. `public_id`(UUID)가 아닙니다.
- `FundingGoalFailed`/`FundingCancelledByMember`(이 서비스가 **구독**하는 쪽)는 **펀딩 1건당 1개씩** 발행됩니다. payload에 `paymentId`가 없으므로 이 서비스가 자기 `payments` 테이블에서 `funding_id`로 완료된 결제(`payments.completed_funding_id` 유니크 인덱스)를 직접 찾아야 합니다.

> **문서 정합성 안내**: `PaymentFunctionalSpec.md`/`PaymentApiSpec.md`의 `RefundCompleted` payload는 `couponIssuanceId`를 빼는 방향으로 서술돼 있는데, 이는 order-service의 실제 코드와 다릅니다. 구현은 이 CLAUDE.md(실제 코드 기준)를 따르세요.

---

## 가장 시급한 미해결 의존성 — order-service 쪽에 아직 없는 내부 API

PAYMENT-001이 정상 동작하려면 order-service로부터 `memberId`/`status`/`finalAmount`/`couponIssuanceId`/`orderName` 조립 재료를 동기로 받아야 하는데, order-service에 이 엔드포인트가 아직 없고 `fundings` 테이블에 `finalAmount` 컬럼도 없습니다. **이건 별도 이슈(order 연동)에서 다룹니다** — 이 서비스에서는 아래처럼 포트만 만들고 개발을 진행하세요:

```java
public interface OrderFundingClient {
    FundingSnapshot fetch(Long fundingId);
    record FundingSnapshot(java.util.UUID memberId, String status, long finalAmount,
                            String orderName, Long couponIssuanceId) {}
}
```

- 실제 HTTP 호출 구현체(`HttpOrderFundingClient`)는 연동 이슈에서 배선.
- 지금은 고정값을 반환하는 `StubOrderFundingClient`(또는 테스트용 `@TestConfiguration` 빈)로 개발·테스트를 진행.
- 없다고 PAYMENT-001 구현 자체를 미루지 말 것 — 인터페이스 뒤에서 비즈니스 로직/검증/에러 처리까지 다 짜두면, 연동 이슈에서는 구현체 하나만 갈아끼우면 됩니다.

정산(PAYMENT-009/012)도 order-service의 `funding_line_items`/`funding_coupon_applications` 집계 조회가 필요한데 마찬가지로 노출 API가 없습니다 — `OrderSettlementAggregateClient` 같은 포트를 하나 더 두고 동일하게 스텁 처리하세요.

---

## 먼저 읽을 문서
`PaymentFunctionalSpec.md`(PAYMENT-001~017, 전체 서술 포함)/`PaymentApiSpec.md`를 먼저 읽으세요. 단, **이벤트 payload 관련 내용은 위 "⚠️ 가장 먼저 읽을 것" 섹션이 우선**합니다.

## 로컬 실행

- 앱 포트: `8085` (`application-local.yml`, gitignore 대상 — 커밋하지 않음)
- DB 포트: `5436`

```bash
cp services/payment-service/.env.example services/payment-service/.env
cd services/payment-service && docker compose up -d
```

`.env.example` (order-service 패턴 그대로, DB명만 교체):

```
DB_HOST=localhost
DB_PORT=5436
DB_NAME=payment_service
DB_USERNAME=payment_service
DB_PASSWORD=payment_service
```

```groovy
// build.gradle — JPA/Flyway/PostgreSQL/Testcontainers는 루트 build.gradle이 :services:*에 자동 적용
dependencies {
    implementation project(':modules:common-webmvc')
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
}
```

## 구현 순서 권장

1. **서비스 스캐폴딩** — `build.gradle`/`docker-compose.yml`/`application*.yml`/`V1__init_schema.sql`(`PaymentERD.md` DDL 전체), `PaymentServiceApplication(scanBasePackages="com.fundit")`.
2. **결제 시도/승인(PAYMENT-001/002) + 웹훅** — `OrderFundingClient` 포트를 스텁으로 두고 먼저 구현.
3. **PAYMENT-016(아웃박스 워커)** — 2번이 끝나자마자 필요. 이게 없으면 이벤트가 영원히 `payment_event_outbox`에만 쌓입니다.
4. **환불(PAYMENT-003/004/005/006/007/008)** — 이벤트 구독(004/005)부터 골격을 만들어야 트리거가 생깁니다.
5. **정산(PAYMENT-009/010/011/012/013/014/015)** — 결제/환불이 끝나야 집계할 데이터가 생기므로 가장 나중.
6. **PAYMENT-017** — MVP 후순위. order-service가 아직 `PaymentReconciliationRequired`를 발행하지 않으므로 인터페이스+no-op 구현만 두고, 실제 배선은 연동 이슈로.

---

## API별 구현 노트

### PAYMENT-001 `POST /api/v1/payments`
`OrderFundingClient.fetch(fundingId)` 호출 → `memberId` 대조(불일치 403) → `status='PENDING'` 확인(아니면 409) → `Payment(PENDING)` 생성, `amount`/`order_name`/`coupon_issuance_id`를 응답값 그대로 스냅샷. `pg_order_id`는 내부 PK를 노출하지 않는 별도 랜덤값(영문 대소문자/숫자/`-`/`_`, 6~64자)으로 채번. `OrderFundingClient` 호출 실패(타임아웃 포함) → `DEPENDENCY_FAILURE`(503).

### PAYMENT-002 `POST /api/v1/payments/confirm`
요청 `amount`를 PAYMENT-001 스냅샷 값과만 대조(재계산 금지, 불일치 시 토스 API 호출 없이 422) → 토스 승인 API(위젯 시크릿 키) 서버-투-서버 호출 → 성공: `Payment(COMPLETED)` + **같은 트랜잭션**에서 `payment_event_outbox`에 `PaymentCompleted(fundingId, couponIssuanceId)` 적재 → 실패: `Payment(FAILED)`만 기록, 이벤트 없음. 인증 후 10분 초과 → 410.

### 웹훅 `POST /api/v1/payments/webhook/toss`
서명 헤더 검증 실패 시 401, `@LoginUser` 요구 안 함. `PAYMENT_STATUS_CHANGED`/`CANCEL_STATUS_CHANGED` 등 보정용 — 1-2 승인 흐름을 대체하지 않음.

### PAYMENT-003 `GET /api/v1/refunds`
본인(`@LoginUser`) `refund_requests`를 페이지네이션 조회. 참여취소/미달자동/하자/발송지연 4개 유형을 `trigger_type` 구분 없이 통합 응답(금액/수단/예상처리기간/상태).

### PAYMENT-006 `POST /api/v1/refunds/defect`
하자유형(불량/파손/표시광고상이) + 증빙(사진/설명) 첨부해 `refund_requests(trigger_type='DEFECT', status='REQUESTED')` 생성. 증빙 누락 시 `EVIDENCE_REQUIRED`(400). 아직 결제 취소를 실행하지 않음(판매자 승인 대기, PAYMENT-007에서 실행).

### PAYMENT-007 `PATCH /api/v1/refunds/{refundId}/decision`
판매자 본인 소유 건인지 검증(타 판매자 403) → 승인 시 `pg_payment_key` 기준 토스 취소 API 호출, 반품비 차감 시 부분취소(취소금액 < `payments.amount`면 `is_full_refund=false`) → 완료 시 `RefundCompleted(fundingId, couponIssuanceId, POST_SUCCESS_DEFECT, isFullRefund)` 발행. 반려 시 사유 필수(`REASON_REQUIRED`), 이벤트 미발행.

### PAYMENT-008 `POST /api/v1/refunds/shipping-delay`
FS-096 판정 결과(`FulfillmentDelayed`) 확인 후 즉시 처리(단순변심/미달자동과 동일하게 `UNDER_REVIEW` 단계 없음) → 전액 취소 → `RefundCompleted(..., POST_SUCCESS_DELAY, true)` 발행. 이미 발송 시작됨 → `ALREADY_SHIPPED`(409).

### PAYMENT-009 `GET /api/v1/settlements/{settlementBatchId}`
본인(해당 메이커) 배치만 조회 가능(403). `gross_amount`/`platform_fee_amount`(3%)/`coupon_deduction_amount`/`refund_deduction_amount`/`total_amount`와, `OrderSettlementAggregateClient`로 조회한 리워드·옵션별 판매 수량·금액(`lineItems`)을 합성해 응답.

### PAYMENT-010 `GET /api/v1/settlements/{settlementBatchId}/download`
PDF/엑셀 등 파일 생성 후 다운로드 URL 또는 파일 스트림 반환. 파일 생성 라이브러리는 팀 컨벤션 없으니 신규 결정 필요[정책 확인 필요].

### PAYMENT-011 `POST /api/v1/settlements/{settlementBatchId}/disputes`
발송일로부터 7일 이내만 접수 가능(경과 시 `DISPUTE_PERIOD_EXPIRED` 409). 접수 시 대상 `settlement_batches.status`를 `ON_HOLD`로 전환(PAYMENT-015가 이 상태면 지급 대상에서 제외하도록 반드시 확인).

---

## 이벤트 구독/스케줄러 구현 노트

### 공통 원칙 — 브로커가 아직 없다
레포 전체에 메시지 브로커가 아직 배선되어 있지 않습니다(order-service `FundingEventTransport`도 `UnconfiguredFundingEventTransport`만 있고 실제 broker 구현체가 없음). 그렇다고 이벤트 구독 로직 자체를 미루지 마세요 — **발행 쪽(이 서비스가 order-service로 보내는 것)과 구독 쪽(이 서비스가 order-service로부터 받는 것)을 대칭적으로 인터페이스 뒤에 완성**해두면 됩니다.

- **발행(Producer)**: PAYMENT-016 = 아웃박스 패턴. `payment_event_outbox`에 트랜잭션 안에서 적재 → `PaymentEventOutboxWorker`(`@Scheduled`)가 `PaymentEventTransport` 인터페이스로 전달 시도 → 브로커 미확정 구간은 `UnconfiguredPaymentEventTransport`가 예외를 던져 재시도 상태로 남김(로깅만 하고 성공 취급 안 함) — order-service `FundingEventOutboxWorker`/`FundingEventTransport`/`UnconfiguredFundingEventTransport` 3종 세트를 그대로 본뜰 것.
- **구독(Consumer, PAYMENT-004/005/017)**: 비즈니스 로직은 "이벤트 레코드를 입력받는 애플리케이션 서비스 메서드"로 완전히 구현하고 단위 테스트도 이 메서드를 직접 호출해서 짭니다(`GoalFailedAutoRefundService.handle(FundingGoalFailedEvent event)`처럼). 실제로 이 메서드를 누가 호출하는지(Kafka 리스너/REST 콜백/기타)는 브로커가 정해지면 그때 얇은 어댑터 하나만 추가하면 됩니다 — 지금은 그 어댑터를 만들지 않고 비워둬도 되고, 골격만 원한다면 `FundingEventSubscriber` 인터페이스(예: `onGoalFailed`, `onCancelledByMember`)를 만들고 아직 아무도 구현하지 않은 상태로 둬도 무방합니다. **핵심은 서비스 레이어(트랜잭션/멱등성/이벤트 발행까지 포함한 전체 로직)를 완성하는 것이지, 브로커 배선이 아닙니다.**

### PAYMENT-004 — 이벤트 구독(`FundingCancelledByMember`)
payload: `(fundingId, projectId, memberId)`. `funding_id`로 완료 결제 조회 → 토스 전액 취소 → `refund_requests(trigger_type='SIMPLE_CHANGE_OF_MIND', status='COMPLETED', is_full_refund=true)` 즉시 생성(중간 단계 없음) → `RefundCompleted(..., CANCELLED_BY_MEMBER, true)` 발행. 중복 수신 시 이미 `CANCELLED` 상태면 토스 API 재호출 없이 무시(멱등).

### PAYMENT-005 — 이벤트 구독(`FundingGoalFailed`)
payload: `(fundingId, projectId)`. **펀딩 1건당 1개**이므로 "일괄 처리"로 짜지 말 것. 완료 결제 조회 → 토스 전액 취소(환불비 미부과) → `refund_requests(trigger_type='GOAL_FAILED_AUTO', is_full_refund=true)` → `RefundCompleted(..., GOAL_FAILURE_AUTO_REFUND, true)` 발행. 원 결제수단 환불 불가 시 `alternate_refund_account` 입력 플로우로 전환(즉시 실패 처리 금지).

### PAYMENT-012 — 배치(정산 처리 시 내부 호출, REST/이벤트 트리거 없음)
PAYMENT-013/014 배치 생성 로직 내부에서 호출되는 계산 스텝. `OrderSettlementAggregateClient`로 `funding_coupon_applications`(issuer_type='MAKER') 조회해 `coupon_deduction_amount` 산출, 플랫폼 발급 쿠폰은 차감하지 않음. 환불로 쿠폰 복원 시 재조정 로직 포함.

### PAYMENT-013 — 이벤트 구독(`FundingSucceeded`) + 5영업일 스케줄
`FundingSucceeded` 수신 시 즉시 배치를 만들지 않고, "달성확정일+5영업일" 실행 대상으로 등록(예: `settlement_schedule` 내부 큐 테이블 또는 배치 대상 판정 쿼리에 달성일 기준 필터링) → 스케줄러가 도래한 건에 대해 `gross_amount`/`platform_fee_amount`(3%)/PAYMENT-012 결과/`refund_deduction_amount` 산출 → `total_amount = (gross - fee - coupon - refund) * 70%` → `settlement_batches(batch_type='INTERIM', status='PENDING')` + `settlement_batch_items` 생성. **동일 기간 중복 생성 방지**(유니크 제약 또는 존재 여부 확인 후 스킵).

### PAYMENT-014 — 스케줄러(배송완료 +14일)
대상 프로젝트에 미해결 하자환불 검토 건(`refund_requests.status='UNDER_REVIEW'`)이 남아있으면 이번 배치에서 제외하고 다음 주기에 재계산. 선정산 이후 발생한 환불/쿠폰 복원분 반영해 `settlement_batches(batch_type='FINAL')` 생성.

### PAYMENT-015 — 스케줄러(매주 금요일)
`status='PENDING'`(이의신청 기간 경과) 배치만 지급 처리 후 `status='PAID'`, `processed_at` 기록. `status='ON_HOLD'`(PAYMENT-011로 이의신청 접수된 건) 제외. 지급 실패(계좌 오류 등) 시 배치를 `PENDING`으로 유지하고 재시도 대상 표시.

### PAYMENT-016 — 내부 스케줄러(폴링)
위 "공통 원칙" 참고. `published_at IS NULL`인 `payment_event_outbox` 행을 배치 크기만큼 폴링 → 실패 시 `attempt_count` 증가/`last_error` 기록, `published_at`은 채우지 않음.

### PAYMENT-017 — 이벤트 구독(`PaymentReconciliationRequired`, order-service가 발행 예정)
order-service가 아직 이 이벤트를 발행하지 않으므로(아래 "정책값" 참고) 인터페이스와 no-op 수준 구현만 두고 실제 트리거는 연동 이슈로 미룰 것. 발행되면: PAYMENT-004와 동일하게 토스 전액취소 → `refund_requests(trigger_type=신규값, 예: 'SYSTEM_RECONCILIATION')` 생성.

---

## 도메인 테이블 (스키마 요약 — 전체 DDL은 `PaymentERD.md` 참고)

- `payment.payments` — `funding_id`(Long, FK 아님), `pg_order_id`, `pg_payment_key`, `amount`/`order_name`(PAYMENT-001 스냅샷), `coupon_issuance_id`(신규 — `PaymentERD.md`에 없으니 구현 시 컬럼 추가), `status`(`PENDING`/`COMPLETED`/`FAILED`/`CANCELLED`), `completed_funding_id`(생성 컬럼, 유니크 제약으로 "펀딩당 완료 결제 1건" 강제).
- `payment.payment_event_outbox` — `PaymentCompleted`/`RefundCompleted` 발행용 아웃박스(PAYMENT-016).
- `refund.refund_requests` — `trigger_type`(`SIMPLE_CHANGE_OF_MIND`/`GOAL_FAILED_AUTO`/`DEFECT`/`SHIPPING_DELAY`, 위 매핑표로 `RefundReason` 변환), `is_full_refund`.
- `settlement.settlement_batches`/`settlement_batch_items`/`settlement_disputes`/`settlement_holds` — 정산. `lineItems`/쿠폰 집계는 `OrderSettlementAggregateClient`로 조회(스텁 처리, 위 "가장 시급한 미해결 의존성" 참고).

---

## 핵심 설계 결정 (구현 시 반드시 지킬 것)

### Toss 연동
- **결제위젯 SDK 방식**: 결제수단 선택/약관동의 UI는 프론트엔드가 직접 렌더링. 백엔드는 "결제 시도 준비"(PAYMENT-001)와 "서버-투-서버 승인"(PAYMENT-002)만 담당.
- **금액은 절대 재계산하지 않는다**: PAYMENT-001에서 받은 `finalAmount`를 고정 저장하고, PAYMENT-002는 저장값과 요청값만 대조.
- **위젯 시크릿 키 ≠ API 개별연동 키**: 승인/취소 API 호출에는 반드시 위젯 전용 시크릿 키를 쓴다(환경변수로 분리, 하드코딩 금지).

### 서비스 간 통신/이벤트
- `Funding.status`/`coupon_issuances`를 직접 쓰지 않는다 — 이벤트로만 통지(DB-per-service 원칙).
- 이벤트 발행/구독 모두 인터페이스 뒤에서 완성하고, 실제 브로커 배선은 미확정 상태로 남겨둔다(위 "공통 원칙" 참고).
- order-service 내부 API 호출에는 반드시 connect/read 타임아웃을 건다.

### 인증 — 이 서비스는 신형 방식을 쓸 것
레포에 인증 방식이 두 갈래(order/project-service는 레거시 `X-Account-Id`+로컬 리졸버, auth/member-service는 신형 `X-User-Id`/`X-User-Roles`+`X-Internal-Api-Key`+`@LoginUser CurrentUser`)로 갈려 있습니다. **이 서비스는 신형을 쓰세요** — 돈이 오가는 서비스라 내부 엔드포인트 보호가 특히 중요합니다.

```java
@SpringBootApplication(scanBasePackages = "com.fundit")
public class PaymentServiceApplication { ... }
```

```java
@PostMapping("/api/v1/payments")
public PaymentCreateResponse create(@LoginUser CurrentUser user, @Valid @RequestBody PaymentCreateRequest request) { ... }
```

`application-dev.yml`/`application-local.yml`에 `internal-api.key`를 설정해야 필터 빈이 뜹니다. 웹훅 엔드포인트는 `X-User-Id`가 없는 요청이라 필터가 자동으로 통과시키므로 별도 조치가 필요 없고, 서명 검증만 직접 추가하면 됩니다.

---

## 에러 코드

`PaymentErrorCode implements ErrorCode`(서비스당 flat enum 1개). `CommonErrorCode`에 이미 있는 `INVALID_INPUT`/`UNAUTHORIZED`/`FORBIDDEN`/`NOT_FOUND`/`CONFLICT`/`RESOURCE_EXPIRED`/`BUSINESS_RULE_VIOLATION`/`DEPENDENCY_FAILURE`는 재정의하지 않는다.

| 코드 | HTTP | 상황 |
|---|---|---|
| `FUNDING_NOT_PENDING` | 409 | 결제 시도 대상 주문이 PENDING 아님 |
| `PAYMENT_NOT_PENDING` | 409 | 승인 대상 결제가 PENDING 아님 |
| `PAYMENT_AMOUNT_MISMATCH` | 422 | 승인 요청 금액과 저장 금액 불일치 |
| `PAYMENT_EXPIRED` | 410 | 토스 인증 후 10분 초과 |
| `PG_CONFIRM_FAILED` / `PG_CANCEL_FAILED` | 422 | 토스 승인/취소 API 실패 |
| `WEBHOOK_SIGNATURE_INVALID` | 401 | 토스 웹훅 서명 검증 실패 |
| `EVIDENCE_REQUIRED` / `REASON_REQUIRED` | 400 | 하자환불 신청/반려 시 필수값 누락 |
| `ALREADY_SHIPPED` | 409 | 발송지연 취소 신청 시점에 이미 발송됨 |
| `DISPUTE_PERIOD_EXPIRED` | 409 | 정산 이의신청 기간(7일) 경과 |

order-service 내부 API 호출 실패는 신규 코드 없이 `CommonErrorCode.DEPENDENCY_FAILURE`(503)를 그대로 쓴다.

## 이 서비스에서 절대 하지 말아야 할 것

- order-service의 `fundings`/`coupon_issuances` 테이블을 직접 쓰지 말 것 — 이벤트로만 통지
- `RefundCompleted` payload에서 `couponIssuanceId`를 생략하지 말 것
- PAYMENT-001에서 받은 `finalAmount`를 PAYMENT-002에서 재계산/재조회하지 말 것
- 결제 승인 성공 처리와 아웃박스 적재를 별도 트랜잭션으로 분리하지 말 것
- 브로커가 없다고 이벤트 발행/구독 비즈니스 로직 자체를 생략하지 말 것 — 인터페이스 뒤에서 완성해둘 것
- PAYMENT-005를 "프로젝트 단위 일괄 처리"로 짜지 말 것(펀딩 1건당 1이벤트)
- 토스 웹훅 엔드포인트에 로그인 인증을 걸지 말 것
- 시크릿 키를 코드/설정 파일에 하드코딩하지 말 것

## 정책값 / 확인 필요 사항

- **[최우선]** order-service 내부 API(`OrderFundingClient`, `OrderSettlementAggregateClient`) 및 `fundings.final_amount` 컬럼 부재 — 별도 연동 이슈에서 처리.
- **레이스 컨디션 보상 미구현**: order-service가 아직 `PaymentReconciliationRequired`를 발행하지 않음(PAYMENT-017 대상 이벤트 없음) — 연동 이슈에서 함께 처리.
- `RefundReason.CANCELLED_BY_MEMBER` 실제 발행 필요 여부 재확인.
- PAYMENT-010 파일(PDF/엑셀) 생성 라이브러리 미정.
- 적립금(`point_transactions`) MVP 포함 여부 미정.
- 게이트웨이(`platform/gateway-service`)에 payment-service 라우트 미등록 — 스캐폴딩 시점에 추가 필요(웹훅 경로는 인증 없이 통과하는 라우트여야 함).

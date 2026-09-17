## 1. PAYMENT-001 — 결제 시도 생성(결제위젯 렌더링 준비)

- **PRD 코드**: FL_B_PY_01_03
- **권한**: 구매자
- **담당 서비스**: payment-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4·S7·S9] 결제위젯 연동 키(위젯 클라이언트 키/시크릿 키)는 "API 개별연동 키"와 별도 키 페어이며, 시크릿 키는 코드에 하드코딩하지 않고 분리 보관(S7·V10) / 결제금액은 서버 재계산값 사용, 클라이언트 전달값 신뢰 금지(S4) / 카드정보 등은 토스가 위탁 처리하므로 자체 저장하지 않음, 전송은 HTTPS(S9) / 본인 주문만 결제 시도 가능(S4) — 소유권 검증은 order-service 내부 API가 반환한 `memberId`와 `@LoginUser CurrentUser.id`(`X-User-Id`)를 대조하는 방식으로 수행
- **소분류**: 결제 시도 생성
- **예외 처리**: 대상 주문이 `PENDING` 아님 → 409 / 이미 `pg_order_id`가 발급된 결제 시도가 있으면 재사용(중복 생성 방지) / order-service 내부 API 호출 실패(타임아웃·5xx) → 503, 결제 시도 자체를 생성하지 않음(서버 간 장애 시 결제 진행 차단이 원칙) / order-service가 반환한 `memberId`가 `CurrentUser.id`와 불일치 → 403
- **요구사항**: 주문에 대한 결제 시도를 생성하고, 결제위젯 렌더링에 필요한 값을 발급한다
- **우선순위**: MVP
- **입력값**: orderId(=fundingId). 호출자 식별은 `@LoginUser CurrentUser`
- **중분류**: 펀딩 참여·결제
- **처리 내용(기술)**: ① `OrderFundingClient.fetch(fundingId)`로 스냅샷을 받는다. 기본은 `StubOrderFundingClient`. HTTP 모드(`order.integration.funding-client.mode=http`)는 `GET /internal/fundings/{fundingId}`를 `X-Internal-Api-Key`로 호출하고 `{ memberId, sellerId, status, finalAmount, orderName, couponIssuanceId }`를 기대한다. **order-service 실제 응답은 `{ projectId, memberId, fundingPublicId }`뿐**이라 HTTP 모드를 켜도 결제에 필요한 필드가 비어 있다 ② 스냅샷 `memberId` == `CurrentUser.id` 검증(불일치 시 403), `status='PENDING'` 검증(아니면 409) ③ `Payment(PENDING)` 레코드 생성 → 토스 규격(영문 대소문자/숫자/`-`/`_`, 6~64자)에 맞는 `pg_order_id` 채번(내부 PK 노출 방지를 위해 별도 랜덤값 사용) ④ ①에서 받은 `finalAmount`/`orderName`/`couponIssuanceId`를 `payments`에 그대로 저장(이후 PAYMENT-002 금액 검증의 기준값이 되므로 재계산하지 않고 이 시점 값을 고정. `couponIssuanceId`는 `payment.completed.v1` 발행 시 그대로 실어 보냄) ⑤ 응답 반환. **프론트엔드는 이 값들로 결제위젯을 초기화·렌더링한다**: ⓐ `tossPayments.widgets({ customerKey })` 위젯 인스턴스 생성(`customerKey`는 백엔드가 별도로 발급하지 않고, 로그인 회원의 `member_id`(UUID)를 그대로 사용 — 무작위·비유추 값 요건을 이미 만족) ⓑ `widgets.setAmount({ value: amount })` ⓒ `widgets.renderPaymentMethods()`로 결제수단 선택 UI 렌더링 ⓓ `widgets.renderAgreement()`로 약관 동의 UI 렌더링(동의 상태는 위젯 내부에서 관리, 우리 DB에 저장하지 않음) ⓔ 결제하기 버튼 클릭 시 `widgets.requestPayment({ orderId, orderName, successUrl, failUrl })` 호출. **이 모든 위젯 렌더링·호출은 프론트엔드가 직접 수행하며 백엔드를 거치지 않는다**
- **출력값**: `paymentId, pg_order_id, amount, orderName` (결제위젯 초기화·`requestPayment` 호출에 필요한 값)
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: 수정 — 기존 "PG 결제 요청 결과(리다이렉트 정보 등)"는 토스의 요청·승인 분리 구조와 맞지 않아, 백엔드 역할을 "결제 시도 준비"로 정정했고, 결제위젯 특유의 `customerKey`/약관동의 위젯 절차를 명시. `amount`/`orderName`의 출처를 order-service 내부 API 동기 호출로 명시하고, 소유권은 `X-User-Id`/`@LoginUser` 기준으로 검증

---

## 2. PAYMENT-002 — 결제 승인 처리

- **PRD 코드**: FL_B_PY_01_03
- **권한**: 구매자
- **담당 서비스**: payment-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4·S7·S9] 응답으로 받은 `amount`를 서버에 저장된 주문 금액과 반드시 대조 검증(S4, 위변조 방지) / 토스 결제승인 API는 **결제위젯 연동 키 중 시크릿 키**로 서버-투-서버 호출(S7, "API 개별연동 키"의 시크릿 키와 혼동 주의) / 결제 관련 정보 저장 시 암호화(S9) / 본인 결제만 승인(`CurrentUser.id` 대조)
- **소분류**: 결제 승인 처리
- **예외 처리**: 대상 결제가 `PENDING` 아님 → 409 / **인증 완료 후 10분 초과** → 토스 측에서 결제 자체가 자동 만료되므로 재시도가 아닌 결제위젯 재호출 안내 / 요청 `amount`와 저장된 금액 불일치 → 승인 API 호출 자체를 막고 422 / 승인 API 응답 실패 → `Payment(FAILED)` 기록만 하고 `Funding.status`는 `PENDING` 유지(재시도 허용, 재고 원복하지 않음) — 사용자는 동일 주문서로 복귀해 재시도 가능(13.3.3)
- **요구사항**: 결제위젯 인증 결과를 서버에서 최종 승인하고, 주문 상태 반영을 위한 이벤트를 발행한다
- **우선순위**: MVP
- **입력값**: `paymentKey, orderId, amount` (`successUrl` 리다이렉트 후 프론트엔드가 전달). 호출자 식별은 `@LoginUser CurrentUser`
- **중분류**: 펀딩 참여·결제
- **처리 내용(기술)**: ① 전달받은 `orderId`(=`pg_order_id`) 기준으로 결제 시도 조회, 본인 소유 검증 ② `amount` 일치 여부 검증(PAYMENT-001 시점 스냅샷값과 대조, order-service 재조회하지 않음) ③ 토스 결제승인 API(`POST /v1/payments/confirm`, 위젯 시크릿 키 사용) 서버-투-서버 호출 ④ 성공: `pg_payment_key` 저장 + `Payment(COMPLETED)` + `payment_method`/`easy_pay_provider` 응답값 반영 ⑤ 같은 트랜잭션에서 `payment_event_outbox`에 `PaymentCompleted` 적재(order-service DB를 직접 쓰지 않고, PAYMENT-016 워커가 `payment.completed.v1`로 비동기 발행). **Kafka 페이로드는 `{ eventId, fundingId, couponIssuanceId }`** — `paymentId`/`paidAt`은 브로커로 나가지 않는다. order-service가 구독해 자체적으로 `Funding.status=FUNDING_IN_PROGRESS` 전이를 수행 ⑥ 실패: `Payment(FAILED)` 기록만 하고 `Funding.status`는 손대지 않음(order-service가 그대로 `PENDING` 유지)
- **출력값**: 결제 승인 결과(`Payment.status=COMPLETED`) — `Funding.status`는 이 응답에 포함하지 않음(비동기 전이이므로 order-service의 `GET /orders/{orderId}` 조회 결과와 일시적으로 다를 수 있음, 프론트엔드는 결제 승인 응답 자체를 성공 판단 기준으로 사용하고 주문 상태 화면은 짧은 지연을 허용해야 함)
- **트리거 방식**: API 호출(프론트 → 백엔드, `successUrl` 리다이렉트 이후). 토스 웹훅(`PAYMENT_STATUS_CHANGED` 등)은 이 흐름을 보완하는 비동기 상태 통지 수단으로 별도 구독(승인 완료 이후 발생하는 상태 변경 보정용). 웹훅은 서명 헤더가 아니라 본문 `data.secret`을 검증하며, 모르는 `paymentKey`는 200으로 무시한다
- **검토의견(변경사항)**: 수정 — 트리거를 "이벤트 구독(PG 콜백)"에서 "API 호출"로 정정, 승인 유효시간(10분) 제약 및 서버-투-서버 승인 호출 절차 반영, 위젯 전용 시크릿 키 사용을 명시. 기존 "성공 시 Funding.status=FUNDING_IN_PROGRESS"는 payment-service가 order-service DB를 직접 쓰는 것으로 해석될 여지가 있어 DB-per-service 원칙 위반이었음 → **이벤트 발행으로 전면 수정**. Kafka 계약은 order-service `PaymentEventListener.PaymentCompletedEvent(fundingId, couponIssuanceId)`와 맞춘다

---

## 3. PAYMENT-003 — 환불 신청/처리 통합 내역 조회

- **PRD 코드**: FL_B_MY_03_01
- **권한**: 구매자
- **담당 서비스**: payment-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4·S9] 본인 환불 내역만 조회 가능(S4) — `@LoginUser CurrentUser.id` 기준 / 환불 계좌 등 금융정보는 저장·전송 시 암호화(S9)
- **소분류**: 환불 신청/처리 통합 내역 조회
- **예외 처리**: 로드 실패 → 안내+재시도
- **요구사항**: 환불 유형과 무관하게 신청·처리 내역을 통합 조회한다
- **우선순위**: MVP
- **입력값**: 페이지네이션. 호출자 식별은 `@LoginUser CurrentUser`
- **중분류**: 마이페이지
- **처리 내용(기술)**: 참여취소/미달자동/하자/지연취소/시스템재조정 유형을 `trigger_type` 구분 없이 통합 조회, 상태(신청/검토/승인/진행중/완료/반려) 조회
- **출력값**: 환불 내역 목록(금액,수단,예상처리기간,상태)
- **트리거 방식**: API 호출

---

## 4. PAYMENT-004 — 참여 취소(단순변심) 환불 실행

- **PRD 코드**: FL_B_RF_01_01
- **권한**: 구매자
- **담당 서비스**: payment-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4] 이벤트로 전달된 fundingId 소유권은 발행 주체(order-service)가 이미 검증했다는 전제, payment-service는 결제 취소·환불 처리만 수행
- **소분류**: 참여 취소(단순변심) 환불 실행
- **예외 처리**: 토스 취소 API 실패 → 재시도·고객센터 안내 / 이미 `CANCELLED`면 토스 API 재호출 없이 무시(멱등)
- **요구사항**: 취소가 확정된 주문에 대해 결제를 취소하고 환불을 실행한다
- **입력값**: `funding.cancelled-by-member.v1` 이벤트 `{ eventId, fundingId, projectId, memberId }` (`paymentId` 없음 — `fundingId`로 완료 결제를 조회)
- **우선순위**: MVP
- **중분류**: 환불
- **처리 내용(기술)**: Kafka 리스너가 `FundingLifecycleEventSyncService`로 위임 → `fundingId`로 완료 결제 조회 → `payments.pg_payment_key` 기준 토스 취소 API(`POST /v1/payments/{paymentKey}/cancel`, `cancelAmount`=전액) 호출 → 성공 시 `payment_cancellations`에 `transactionKey`/취소금액 기록, `Payment.status=CANCELLED`, `refund_requests`(`trigger_type=SIMPLE_CHANGE_OF_MIND`, `is_full_refund=true`, `status=COMPLETED`) 생성 → 같은 트랜잭션에서 `payment_event_outbox`에 `RefundCompleted` 적재. **Kafka `refund.completed.v1` 페이로드는 `{ eventId, fundingId, couponIssuanceId, refundReason, fullRefund }`** (`refundReason`=`CANCELLED_BY_MEMBER`, `fullRefund`=`true`). 동시에 `notification.raised.v1`(notifType=`REFUND_STATUS`, 완료)도 적재한다
- **출력값**: 취소 처리 결과
- **트리거 방식**: 이벤트 구독(`funding.cancelled-by-member.v1`)
- **검토의견(변경사항)**: 수정 — `RefundCompleted` 이벤트 발행 절차를 명시(order-service ORDER-015가 이 이벤트를 구독). Kafka 필드명은 `refundReason`/`fullRefund`이며 `couponIssuanceId`를 반드시 포함한다. 입력은 `(fundingId, projectId, memberId)`이다

---

## 5. PAYMENT-005 — 미달 자동환불 실행

- **PRD 코드**: FL_B_RF_01_02
- **권한**: 시스템
- **담당 서비스**: payment-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S1·S9] 환불 계좌 등 금융정보 저장·전송 암호화(S9) / 배치 처리 바인딩 변수 사용(S1)
- **소분류**: 미달 자동환불 실행
- **예외 처리**: 원 결제수단 환불 불가(카드만료 등) → 참여자에게 대체 계좌 정보(은행명/예금주/계좌번호) 입력 요청, `refund_requests.alternate_refund_account` 대기 상태로 전환하고 `notification.raised.v1`(대체 계좌 필요) 발행 / 토스 취소 API 실패가 아닌 다른 오류는 재시도·고객센터 안내
- **요구사항**: 목표 미달 주문의 결제를 자동 환불한다
- **우선순위**: MVP
- **입력값**: `funding.goal-failed.v1` 이벤트 `{ eventId, fundingId, projectId }`
- **중분류**: 환불
- **처리 내용(기술)**: **펀딩 1건당 1이벤트**이므로 프로젝트 단위 일괄 처리로 짜지 않는다. 완료 결제 조회 → `pg_payment_key` 기준 토스 전액 취소(환불비 미부과) → `refund_requests`(`trigger_type=GOAL_FAILED_AUTO`, `is_full_refund=true`) → Kafka `refund.completed.v1`(`refundReason`=`GOAL_FAILURE_AUTO_REFUND`, `fullRefund`=`true`, `couponIssuanceId` 포함) 및 `notification.raised.v1` 적재
- **출력값**: 환불 처리 결과
- **트리거 방식**: 이벤트 구독(`funding.goal-failed.v1`)
- **검토의견(변경사항)**: `RefundCompleted` 이벤트 발행 절차 추가(PAYMENT-004와 동일한 이유). 입력에 `paymentId`가 없다

---

## 6. PAYMENT-006 — 하자환불 신청

- **PRD 코드**: FL_B_RF_01_03
- **권한**: 구매자
- **담당 서비스**: payment-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S2·S4·S5] 하자 증빙 사진 업로드 시 확장자 화이트리스트·크기 제한·파일명 변경 적용(S5) / 하자 설명 텍스트 서버 검증 및 출력 인코딩(S2) / 본인 주문만 신청 가능(S4) — `@LoginUser CurrentUser`
- **소분류**: 하자환불 신청
- **예외 처리**: 증빙 누락 → `EVIDENCE_REQUIRED`(400)로 신청 차단 / 완료된 결제 없음 → `NOT_FOUND`(404, `FUNDING_NOT_FOUND` 코드 없음)
- **요구사항**: 수령한 리워드의 하자를 사유로 환불을 신청한다
- **우선순위**: MVP
- **입력값**: fundingId, 하자유형(`DEFECTIVE`/`DAMAGED`/`DIFFERENT_FROM_DESCRIPTION`), 증빙자료. 호출자 식별은 `@LoginUser CurrentUser`
- **중분류**: 환불
- **처리 내용(기술)**: 하자 유형 선택, 증빙(사진·설명) 첨부 후 `refund_requests(trigger_type='DEFECT', status='REQUESTED')` 접수. 아직 결제 취소를 실행하지 않음(판매자 승인 대기, PAYMENT-007에서 실행)
- **출력값**: 신청 접수 결과(`refundId`, `status=REQUESTED`)
- **트리거 방식**: API 호출

---

## 7. PAYMENT-007 — 하자환불 검토/승인/반려

- **PRD 코드**: FL_B_RF_01_03
- **권한**: 판매자
- **담당 서비스**: payment-service
- **대분류**: 판매자
- **보안/권한 고려사항**: [S2·S4] 승인/반려 사유 텍스트 출력 시 인코딩(S2) / 해당 주문의 판매자 본인 여부 서버 검증(`OrderFundingClient`가 반환한 `sellerId`와 `CurrentUser.id` 대조), 타 판매자의 환불 건 처리 차단(S4)
- **소분류**: 하자환불 검토/승인/반려
- **예외 처리**: 반려 시 사유 필수 입력(`REASON_REQUIRED`)
- **요구사항**: 판매자(또는 운영)가 하자환불 신청을 검토해 승인·반려한다
- **우선순위**: MVP
- **입력값**: refundId, 승인/반려, 사유. 호출자 식별은 `@LoginUser CurrentUser`
- **중분류**: 환불
- **처리 내용(기술)**: 신청 검토 후 승인 시 **같은 요청 안에서** `pg_payment_key` 기준 토스 전액 취소 API를 호출하고, 성공하면 `refund_requests.status=COMPLETED`를 즉시 반환한다(`PROCESSING`을 응답하지 않음). **MVP는 전액 환불만 수행**한다 — 반품비 차감 등 부분취소 금액 산정이 미확정이라 `payments.amount` 전액을 취소하고 `is_full_refund=true`로 기록한다. 완료 시 `refund.completed.v1`(`refundReason`=`POST_SUCCESS_DEFECT`, `fullRefund`=`true`, `couponIssuanceId` 포함)와 `notification.raised.v1`(완료)을 적재. 반려 시 사유 기록, `RefundCompleted`는 발행하지 않고 `notification.raised.v1`(반려)만 발행
- **출력값**: 처리 결과(`status=COMPLETED` 또는 `REJECTED`)
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: 승인 응답은 즉시 `COMPLETED`. 부분취소는 MVP 범위 밖

---

## 8. PAYMENT-008 — 발송지연 결제취소 신청

- **PRD 코드**: FL_B_RF_01_04
- **권한**: 구매자
- **담당 서비스**: payment-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4] 본인 주문만 신청 가능, orderId 조작 차단 — `@LoginUser CurrentUser`
- **소분류**: 발송지연 결제취소 신청
- **예외 처리**: 신청 시점에 이미 발송 시작됨(`isAlreadyShipped=true`) → `ALREADY_SHIPPED`(409), 취소 불가 안내+배송현황 확인 유도
- **요구사항**: 발송 지연을 사유로 결제 취소를 신청한다
- **우선순위**: MVP
- **입력값**: fundingId. 호출자 식별은 `@LoginUser CurrentUser`
- **중분류**: 환불
- **처리 내용(기술)**: fulfillment-service 내부 API(`GET /internal/fundings/{fundingId}/fulfillment-status`, 헤더 `X-Internal-Api-Key`) 응답의 **`isAlreadyShipped`만 확인**한다. `FulfillmentDelayed` 이벤트는 구독하지 않는다. 발송 전이면 UNDER_REVIEW 단계 없이 즉시 전액 취소(환불비 미부과), `refund_requests.trigger_type=SHIPPING_DELAY`, `is_full_refund=true`. 원 결제수단 환불이 불가한 경우 PAYMENT-005와 동일하게 대체 계좌 대기 상태로 전환 → 취소 완료 시 `refund.completed.v1`(`refundReason`=`POST_SUCCESS_DELAY`, `fullRefund`=`true`) 및 `notification.raised.v1` 적재
- **출력값**: 신청 접수 결과(`status=COMPLETED`)
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: FS-096 `FulfillmentDelayed` 이벤트 구독이 아니라 `isAlreadyShipped` 동기 조회로 구현됨

---

## 9. PAYMENT-009 — 정산 내역서 조회

- **PRD 코드**: FL_B_SE_01
- **권한**: 판매자
- **담당 서비스**: payment-service
- **대분류**: 판매자
- **보안/권한 고려사항**: [S4] 본인(해당 메이커) 배치만 조회 — `@LoginUser CurrentUser`
- **소분류**: 정산 내역서 조회
- **예외 처리**: 없음/타 판매자 → 404/403
- **요구사항**: 선정산·최종정산 내역서를 조회한다
- **우선순위**: MVP
- **입력값**: settlementBatchId. 호출자 식별은 `@LoginUser CurrentUser`
- **중분류**: 정산
- **처리 내용(기술)**: `gross_amount` / `platform_fee_amount`(3%) / `coupon_deduction_amount` / `refund_deduction_amount` / `total_amount`와, `OrderSettlementAggregateClient`로 조회한 리워드·옵션별 판매 수량·금액(`lineItems`)을 합성해 응답. `totalAmount = max(0, gross - fee - coupon - refund)`(선정산·최종정산 모두 순액 100%)
- **출력값**: 정산 내역서
- **트리거 방식**: API 호출

---

## 10. PAYMENT-010 — 정산 내역서 다운로드

- **PRD 코드**: FL_B_SE_01
- **권한**: 판매자
- **담당 서비스**: payment-service
- **대분류**: 판매자
- **보안/권한 고려사항**: [S4] 본인 배치만 다운로드 — `@LoginUser CurrentUser`
- **소분류**: 정산 내역서 다운로드
- **예외 처리**: 권한 검증 후 파일 생성 미구현 → `SERVICE_UNAVAILABLE`(503, "정산 내역서 다운로드 기능은 준비 중입니다.")
- **요구사항**: 정산 내역서를 파일로 내려받는다
- **우선순위**: MVP
- **입력값**: settlementBatchId. 호출자 식별은 `@LoginUser CurrentUser`
- **중분류**: 정산
- **처리 내용(기술)**: 배치 존재·소유권만 검증하고, PDF/엑셀 생성 라이브러리가 미정이라 거짓 `downloadUrl`을 만들지 않는다. 검증 통과 후에도 503을 반환한다
- **출력값**: 없음(503)
- **트리거 방식**: API 호출

---

## 11. PAYMENT-011 — 정산 이의 신청

- **PRD 코드**: FL_B_SE_01
- **권한**: 판매자
- **담당 서비스**: payment-service
- **대분류**: 판매자
- **보안/권한 고려사항**: [S4] 본인 배치만 신청 — `@LoginUser CurrentUser`
- **소분류**: 정산 이의 신청
- **예외 처리**: 이의신청 가능 기간(7일) 경과 → `DISPUTE_PERIOD_EXPIRED`(409). 현재 구현은 배치 `createdAt`을 대리 기준으로 사용(실제 발송일 연동 전)
- **요구사항**: 정산 금액에 이의를 신청하고 지급을 보류한다
- **우선순위**: MVP
- **입력값**: settlementBatchId, 사유, 증빙. 호출자 식별은 `@LoginUser CurrentUser`
- **중분류**: 정산
- **처리 내용(기술)**: 접수 시 대상 `settlement_batches.status`를 `ON_HOLD`로 전환. PAYMENT-015는 이 상태를 지급 대상에서 제외한다
- **출력값**: `{ disputeId, status: RECEIVED }`
- **트리거 방식**: API 호출

---

## 12. PAYMENT-012 — 쿠폰 정산 차감 계산

- **PRD 코드**: FL_B_SE_01
- **권한**: 시스템
- **담당 서비스**: payment-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 집계 조회 바인딩 변수 사용
- **소분류**: 쿠폰 정산 차감 계산
- **예외 처리**: order-service 집계 조회 실패 시 배치 생성 경로의 의존성 실패로 처리
- **요구사항**: 정산 배치 생성 시 메이커 발급 쿠폰 할인액을 차감한다
- **우선순위**: MVP
- **입력값**: fundingId (PAYMENT-013/014 내부 호출)
- **중분류**: 정산
- **처리 내용(기술)**: PAYMENT-013/014 배치 생성 로직 내부에서 호출. `OrderSettlementAggregateClient.fetchMakerCouponDeductionAmount`로 메이커 발급 쿠폰만 차감하고, 플랫폼 발급 쿠폰은 차감하지 않음
- **출력값**: `coupon_deduction_amount`
- **트리거 방식**: 배치(정산 처리 시 내부 호출)

---

## 13. PAYMENT-013 — 선정산 배치 생성

- **PRD 코드**: FL_B_SE_01
- **권한**: 시스템
- **담당 서비스**: payment-service
- **대분류**: 판매자
- **보안/권한 고려사항**: [S1] 스케줄 조회 바인딩 변수 사용
- **소분류**: 선정산 배치 생성
- **예외 처리**: 동일 funding의 INTERIM 스케줄이 이미 있으면 재등록하지 않음(멱등) / 정산 대상 결제가 없으면 해당 건 skip
- **요구사항**: 펀딩 성립 후 선정산(INTERIM) 배치를 만든다
- **우선순위**: MVP
- **입력값**: `funding.succeeded.v1` `{ eventId, fundingId, projectId, sellerId, achievedAt }` — order-service `KafkaFundingEventTransport.sendSucceeded`가 `sellerId`/`achievedAt`을 채워 보낸다. 판매자 단위 묶음과 달성확정일+5영업일 계산에 둘 다 필요하다
- **중분류**: 정산
- **처리 내용(기술)**: `FundingSucceeded` 수신 시 즉시 배치를 만들지 않고 `settlement_schedule`(INTERIM, `dueAt`=달성확정일+5영업일)에 등록 → `SettlementScheduleWorker`가 도래한 건을 판매자 단위로 묶어 `gross`/`platform_fee`(3%)/PAYMENT-012/`refund_deduction` 산출 → **`totalAmount = max(0, gross - fee - coupon - refund)` (순액 100%, 70% 선지급 계수 없음)** → `settlement_batches(batch_type='INTERIM', status='PENDING')` + `settlement_batch_items` 생성
- **출력값**: 선정산 배치
- **트리거 방식**: 이벤트 구독(`funding.succeeded.v1`) + 스케줄러
- **검토의견(변경사항)**: 선정산 지급 비율 70% 서술을 제거. Kafka 입력에 `sellerId`/`achievedAt`을 포함

---

## 14. PAYMENT-014 — 최종정산 배치 생성

- **PRD 코드**: FL_B_SE_01
- **권한**: 시스템
- **담당 서비스**: payment-service
- **대분류**: 판매자
- **보안/권한 고려사항**: [S1] 스케줄 조회 바인딩 변수 사용
- **소분류**: 최종정산 배치 생성
- **예외 처리**: 동일 funding의 FINAL 스케줄이 이미 있으면 재등록하지 않음
- **요구사항**: 배송완료 후 최종정산(FINAL) 배치를 만든다
- **우선순위**: MVP
- **입력값**: `shipping.completed.v1` `{ fundingId, projectId, sellerId, completedAt }`
- **중분류**: 정산
- **처리 내용(기술)**: 수신 시 `settlement_schedule`(FINAL, `dueAt`=배송완료+14일)에 등록 → 워커가 도래한 건에 대해 PAYMENT-013과 동일한 산출식(순액 100%)으로 `settlement_batches(batch_type='FINAL')` 생성. payment 쪽 리스너는 구현돼 있고, fulfillment-service의 실제 발행이 붙으면 동작한다
- **출력값**: 최종정산 배치
- **트리거 방식**: 이벤트 구독(`shipping.completed.v1`) + 스케줄러

---

## 15. PAYMENT-015 — 정산 지급 실행

- **PRD 코드**: FL_B_SE_01
- **권한**: 시스템
- **담당 서비스**: payment-service
- **대분류**: 판매자
- **보안/권한 고려사항**: [S1] 지급 대상 조회 바인딩 변수 사용
- **소분류**: 정산 지급 실행
- **예외 처리**: 지급 처리 실패 시 배치를 `PENDING`으로 유지하고 다음 주기 재시도. 실제 계좌 이체(펌뱅킹/PG)는 미연동 — 상태 전이(`PAID`)만 수행
- **요구사항**: 지급 대상 정산 배치를 지급 완료 처리한다
- **우선순위**: MVP
- **입력값**: (스케줄러, 파라미터 없음)
- **중분류**: 정산
- **처리 내용(기술)**: 매주 금요일 cron. `status='PENDING'` 배치만 `PAID`로 전이하고 `processed_at`을 기록. `ON_HOLD`(PAYMENT-011 이의신청)는 제외
- **출력값**: 지급 처리 건수
- **트리거 방식**: 스케줄러(매주 금요일)

---

## 16. PAYMENT-016 — 결제/환불 완료 이벤트 발행(아웃박스 워커)

- **PRD 코드**: -
- **권한**: 시스템
- **담당 서비스**: payment-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 발행 대상 조회 쿼리 바인딩 변수 사용
- **소분류**: 결제/환불 완료 이벤트 발행
- **예외 처리**: 발행 실패(브로커 장애 등) → `attempt_count` 증가, `last_error` 기록 후 다음 주기 재시도 / 특정 건이 N회 이상 연속 실패 → 알림·수동 개입 대상으로 표시[정책 확인 필요, 재시도 상한값 미정]
- **요구사항**: `payment_event_outbox`에 적재된 `PaymentCompleted`/`RefundCompleted` 이벤트를 최종적 일관성으로 안전하게 발행한다
- **우선순위**: MVP(PAYMENT-002/004/005/007/008/017이 이 워커에 의존하므로 사실상 필수)
- **입력값**: (내부 폴링 트리거, 파라미터 없음)
- **중분류**: 공통·인프라
- **처리 내용(기술)**: `published_at IS NULL`인 `payment_event_outbox` 행을 주기적으로 폴링 → `KafkaPaymentEventTransport`가 `payment.completed.v1` / `refund.completed.v1`로 발행 → 성공 시 `published_at` 기록. 전송 실패를 성공으로 취급하지 않는다. 환불 상태 알림은 별도 `notification_outbox` + `notification.raised.v1` 워커가 같은 패턴으로 발행한다
- **출력값**: 발행 처리 결과
- **트리거 방식**: 내부 스케줄러(짧은 주기 폴링)
- **검토의견(변경사항)**: PAYMENT-002/004/005/007/008/017이 `PaymentCompleted`/`RefundCompleted`를 신뢰성 있게(트랜잭션과 함께) 발행할 주체. Kafka 페이로드는 4장의 발행 이벤트 계약과 동일

---

## 17. PAYMENT-017 — 결제-재고만료 충돌 자동 환불 처리

- **PRD 코드**: - (13.3.3 파생 예외 케이스)
- **권한**: 시스템
- **담당 서비스**: payment-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 이벤트 소스 검증(order-service가 발행한 이벤트인지 확인)
- **소분류**: 결제-재고만료 충돌 자동 환불
- **예외 처리**: 토스 취소 API 실패 → 재시도·고객센터 안내(PAYMENT-004와 동일)
- **요구사항**: 결제는 성공했으나 order-service의 재고/주문이 이미 만료 처리된 극히 드문 레이스 컨디션 발생 시, 결제금액을 자동으로 전액 환불한다
- **우선순위**: P1 (발생 확률은 낮으나 발생 시 금전 사고로 이어지므로 MVP 범위에서 반드시 설계는 확정하고, 실제 배치 처리 우선순위는 P1으로 둠)
- **입력값**: `payment.reconciliation-required.v1` `{ fundingId, paymentId }` — order-service가 발행할 이벤트
- **중분류**: 환불
- **처리 내용(기술)**: **payment 쪽은 no-op이 아니다.** `PaymentReconciliationEventKafkaListener` → `PaymentReconciliationService`가 PAYMENT-004와 동일하게 `pg_payment_key` 기준 토스 전액 취소를 실행하고, `refund_requests(trigger_type=SYSTEM_RECONCILIATION, is_full_refund=true)`를 생성한 뒤 `refund.completed.v1`/`notification.raised.v1`을 적재한다. Kafka `refundReason`은 order-service enum에 대응 값이 없어 임시로 `GOAL_FAILURE_AUTO_REFUND`로 매핑한다. **현재 막힌 지점은 order-service가 이 토픽을 아직 발행하지 않는다는 점**이며, 발행되면 바로 동작한다
- **출력값**: 자동 환불 처리 결과
- **트리거 방식**: 이벤트 구독(`payment.reconciliation-required.v1`)
- **검토의견(변경사항)**: 리스너·전액취소 로직은 구현 완료. order-service 발행 연동 대기. `trigger_type`은 `SYSTEM_RECONCILIATION`으로 확정(코드에 존재)

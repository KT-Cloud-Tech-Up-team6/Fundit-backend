## 1. PAYMENT-001 — 결제 시도 생성(결제위젯 렌더링 준비)

- **PRD 코드**: FL_B_PY_01_03
- **권한**: 구매자
- **담당 서비스**: payment-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4·S7·S9] 결제위젯 연동 키(위젯 클라이언트 키/시크릿 키)는 "API 개별연동 키"와 별도 키 페어이며, 시크릿 키는 코드에 하드코딩하지 않고 분리 보관(S7·V10) / 결제금액은 서버 재계산값 사용, 클라이언트 전달값 신뢰 금지(S4) / 카드정보 등은 토스가 위탁 처리하므로 자체 저장하지 않음, 전송은 HTTPS(S9) / 본인 주문만 결제 시도 가능(S4) — **[신규]** 소유권 검증은 order-service 내부 API가 반환한 `memberId`와 `X-Account-Id`를 대조하는 방식으로 수행
- **소분류**: 결제 시도 생성
- **예외 처리**: 대상 주문이 `PENDING` 아님 → 409 / 이미 `pg_order_id`가 발급된 결제 시도가 있으면 재사용(중복 생성 방지) / **[신규]** order-service 내부 API 호출 실패(타임아웃·5xx) → 503, 결제 시도 자체를 생성하지 않음(서버 간 장애 시 결제 진행 차단이 원칙) / **[신규]** order-service가 반환한 `memberId`가 `X-Account-Id`와 불일치 → 403
- **요구사항**: 주문에 대한 결제 시도를 생성하고, 결제위젯 렌더링에 필요한 값을 발급한다
- **우선순위**: MVP
- **입력값**: orderId(=fundingId)
- **중분류**: 펀딩 참여·결제
- **처리 내용(기술)**: ① **[신규]** order-service 내부 API(`GET /internal/fundings/{fundingId}`)를 동기 호출해 `memberId`, `status`, `finalAmount`, `orderName`을 조회 ② `memberId` == `X-Account-Id` 검증(불일치 시 403), `status='PENDING'` 검증(아니면 409) ③ `Payment(PENDING)` 레코드 생성 → 토스 규격(영문 대소문자/숫자/`-`/`_`, 6~64자)에 맞는 `pg_order_id` 채번(내부 PK 노출 방지를 위해 별도 랜덤값 사용) ④ ①에서 받은 `finalAmount`/`orderName`을 `payments.amount`/`payments.order_name`에 그대로 저장(이후 PAYMENT-002 금액 검증의 기준값이 되므로 재계산하지 않고 이 시점 값을 고정) ⑤ 응답 반환. **프론트엔드는 이 값들로 결제위젯을 초기화·렌더링한다**: ⓐ `tossPayments.widgets({ customerKey })` 위젯 인스턴스 생성(`customerKey`는 백엔드가 별도로 발급하지 않고, 로그인 회원의 `member_id`(UUID)를 그대로 사용 — 무작위·비유추 값 요건을 이미 만족) ⓑ `widgets.setAmount({ value: amount })` ⓒ `widgets.renderPaymentMethods()`로 결제수단 선택 UI 렌더링 ⓓ `widgets.renderAgreement()`로 약관 동의 UI 렌더링(동의 상태는 위젯 내부에서 관리, 우리 DB에 저장하지 않음) ⓔ 결제하기 버튼 클릭 시 `widgets.requestPayment({ orderId, orderName, successUrl, failUrl })` 호출. **이 모든 위젯 렌더링·호출은 프론트엔드가 직접 수행하며 백엔드를 거치지 않는다**
- **출력값**: `paymentId, pg_order_id, amount, orderName` (결제위젯 초기화·`requestPayment` 호출에 필요한 값)
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: 수정 — 기존 "PG 결제 요청 결과(리다이렉트 정보 등)"는 토스의 요청·승인 분리 구조와 맞지 않아, 백엔드 역할을 "결제 시도 준비"로 정정했고, 결제위젯 특유의 `customerKey`/약관동의 위젯 절차를 명시. **[이번 개정]** `amount`/`orderName`의 출처가 스펙에 없었던 문제를 order-service 내부 API 동기 호출 절차로 명시하고, 소유권·상태 검증도 이 호출 결과 기준으로 구체화

---

## 2. PAYMENT-002 — 결제 승인 처리

- **PRD 코드**: FL_B_PY_01_03
- **권한**: 구매자
- **담당 서비스**: payment-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4·S7·S9] 응답으로 받은 `amount`를 서버에 저장된 주문 금액과 반드시 대조 검증(S4, 위변조 방지) / 토스 결제승인 API는 **결제위젯 연동 키 중 시크릿 키**로 서버-투-서버 호출(S7, "API 개별연동 키"의 시크릿 키와 혼동 주의) / 결제 관련 정보 저장 시 암호화(S9)
- **소분류**: 결제 승인 처리
- **예외 처리**: 대상 결제가 `PENDING` 아님 → 409 / **인증 완료 후 10분 초과** → 토스 측에서 결제 자체가 자동 만료되므로 재시도가 아닌 결제위젯 재호출 안내 / 요청 `amount`와 저장된 금액 불일치 → 승인 API 호출 자체를 막고 422 / 승인 API 응답 실패 → `Payment(FAILED)` 기록만 하고 `Funding.status`는 `PENDING` 유지(재시도 허용, 재고 원복하지 않음) — 사용자는 동일 주문서로 복귀해 재시도 가능(13.3.3) / **[신규]** 토스 승인은 성공했으나 이벤트 발행 직전 order-service 상태 재확인 결과 이미 `PAYMENT_EXPIRED`(ORDER-013 만료 배치와의 레이스) → `Payment(COMPLETED)`는 그대로 두되 `PaymentCompleted` 대신 즉시 자동 취소 플로우로 분기(PAYMENT-017 참고), 사용자에게는 "재고 확보 실패로 자동 환불됩니다" 안내
- **요구사항**: 결제위젯 인증 결과를 서버에서 최종 승인하고, 주문 상태 반영을 위한 이벤트를 발행한다
- **우선순위**: MVP
- **입력값**: `paymentKey, orderId, amount` (`successUrl` 리다이렉트 후 프론트엔드가 전달)
- **중분류**: 펀딩 참여·결제
- **처리 내용(기술)**: ① 전달받은 `orderId`(=`pg_order_id`) 기준으로 결제 시도 조회 ② `amount` 일치 여부 검증(PAYMENT-001 시점 스냅샷값과 대조, order-service 재조회하지 않음) ③ 토스 결제승인 API(`POST /v1/payments/confirm`, 위젯 시크릿 키 사용) 서버-투-서버 호출 ④ 성공: `pg_payment_key` 저장 + `Payment(COMPLETED)` + `payment_method`/`easy_pay_provider` 응답값 반영 ⑤ **[신규]** 같은 트랜잭션에서 `payment_event_outbox`에 `PaymentCompleted`(paymentId, fundingId, paidAt) 레코드 적재(order-service DB를 직접 쓰지 않고, PAYMENT-016 워커가 비동기 발행 — order-service가 구독해 자체적으로 `Funding.status=FUNDING_IN_PROGRESS` 전이 수행) ⑥ 실패: `Payment(FAILED)` 기록만 하고 `Funding.status`는 손대지 않음(order-service가 그대로 `PENDING` 유지)
- **출력값**: 결제 승인 결과(`Payment.status`) — **[신규]** `Funding.status`는 이 응답에 포함하지 않음(비동기 전이이므로 order-service의 `GET /orders/{orderId}` 조회 결과와 일시적으로 다를 수 있음, 프론트엔드는 결제 승인 응답 자체를 성공 판단 기준으로 사용하고 주문 상태 화면은 짧은 지연을 허용해야 함)
- **트리거 방식**: API 호출(프론트 → 백엔드, `successUrl` 리다이렉트 이후). 토스 웹훅(`PAYMENT_STATUS_CHANGED` 등)은 이 흐름을 보완하는 비동기 상태 통지 수단으로 별도 구독(승인 완료 이후 발생하는 상태 변경 보정용)
- **검토의견(변경사항)**: 수정 — 트리거를 "이벤트 구독(PG 콜백)"에서 "API 호출"로 정정, 승인 유효시간(10분) 제약 및 서버-투-서버 승인 호출 절차 반영, 위젯 전용 시크릿 키 사용을 명시. **[이번 개정, 중요]** 기존 "성공 시 Funding.status=FUNDING_IN_PROGRESS"는 payment-service가 order-service DB를 직접 쓰는 것으로 해석될 여지가 있어 DB-per-service 원칙 위반이었음 → **이벤트 발행으로 전면 수정**. 아울러 order-service의 미결제 만료 배치(ORDER-013)와 동시에 벌어질 수 있는 레이스 컨디션에 대한 예외 처리를 신규로 추가(PAYMENT-017 연계)

---

## 3. PAYMENT-003 — 환불 신청/처리 통합 내역 조회

*(기존과 동일 — 변경 없음)*

- **PRD 코드**: FL_B_MY_03_01
- **권한**: 구매자
- **담당 서비스**: payment-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4·S9] 본인 환불 내역만 조회 가능(S4) / 환불 계좌 등 금융정보는 저장·전송 시 암호화(S9)
- **소분류**: 환불 신청/처리 통합 내역 조회
- **예외 처리**: 로드 실패 → 안내+재시도
- **요구사항**: 환불 유형과 무관하게 신청·처리 내역을 통합 조회한다
- **우선순위**: MVP
- **입력값**: 페이지네이션
- **중분류**: 마이페이지
- **처리 내용(기술)**: 참여취소/미달자동/하자/지연취소 전체 유형 통합, 상태(신청/검토/승인/진행중/완료/반려) 조회
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
- **예외 처리**: 토스 취소 API 실패 → 재시도·고객센터 안내
- **요구사항**: 취소가 확정된 주문에 대해 결제를 취소하고 환불을 실행한다
- **입력값**: `FundingCancelledByMember` 이벤트(fundingId, paymentId)
- **우선순위**: MVP
- **중분류**: 환불
- **처리 내용(기술)**: `FundingCancelledByMember` 이벤트 수신 → `payments.pg_payment_key` 기준 토스 취소 API(`POST /v1/payments/{paymentKey}/cancel`, `cancelAmount`=전액) 호출 → 성공 시 `payment_cancellations`에 `transactionKey`/취소금액 기록, `Payment.status=CANCELLED`, `refund_requests`(`trigger_type=SIMPLE_CHANGE_OF_MIND`, `is_full_refund=true`) 생성 → 결제금액 전액 원 결제수단 환불(수수료 없음) → **[신규]** 같은 트랜잭션에서 `payment_event_outbox`에 `RefundCompleted`(paymentId, fundingId, payload:{triggerType:'SIMPLE_CHANGE_OF_MIND', isFullRefund:true}) 적재
- **출력값**: 취소 처리 결과
- **트리거 방식**: 이벤트 구독(`FundingCancelledByMember`)
- **검토의견(변경사항)**: 수정 — 처리 내용에 토스 취소 API 호출과 `payment_cancellations` 기록 절차를 구체화. **[이번 개정]** `RefundCompleted` 이벤트 발행 절차를 명시(기존 문서는 DB 기록에서 끝나 있었으나, order-service ORDER-015가 이 이벤트를 구독해 쿠폰을 복원하지 않는 판단을 하므로 발행이 반드시 필요함). `couponIssuanceId`는 payload에 넣지 않음 — order-service가 자기 DB에서 fundingId로 직접 조회

---

## 5. PAYMENT-005 — 미달 자동환불 실행

- **PRD 코드**: FL_B_RF_01_02
- **권한**: 시스템
- **담당 서비스**: payment-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S1·S9] 환불 계좌 등 금융정보 저장·전송 암호화(S9) / 배치 처리 바인딩 변수 사용(S1)
- **소분류**: 미달 자동환불 실행
- **예외 처리**: 원 결제수단 환불 불가(카드만료 등) → 참여자에게 대체 계좌 정보(은행명/예금주/계좌번호) 입력 요청, `refund_requests.alternate_refund_account`에 저장 / 토스 취소 API 실패 → 재시도·고객센터 안내
- **요구사항**: 목표 미달 프로젝트의 전체 참여자에게 자동 환불한다
- **우선순위**: MVP
- **입력값**: `FundingGoalFailed` 이벤트
- **중분류**: 환불
- **처리 내용(기술)**: 이벤트 구독 후 해당 프로젝트 전체 참여자 결제에 대해 `pg_payment_key` 기준 토스 취소 API 전액 취소 일괄 처리, `payment_cancellations` 기록(`refund_requests.trigger_type=GOAL_FAILED_AUTO`, `is_full_refund=true`), 환불비 미부과. 원 결제수단 환불이 불가한 경우(토스 취소 API 실패 응답) 대체 계좌 정보 입력 요청 플로우로 전환 → **[신규]** 건별 취소 성공 시마다 `payment_event_outbox`에 `RefundCompleted`(payload:{triggerType:'GOAL_FAILED_AUTO', isFullRefund:true}) 적재
- **출력값**: 환불 처리 결과(대상 건수)
- **트리거 방식**: 이벤트 구독(`FundingGoalFailed`)
- **검토의견(변경사항)**: **[이번 개정]** `RefundCompleted` 이벤트 발행 절차 추가(PAYMENT-004와 동일한 이유)

---

## 6. PAYMENT-006 — 하자환불 신청

*(기존과 동일 — 변경 없음)*

- **PRD 코드**: FL_B_RF_01_03
- **권한**: 구매자
- **담당 서비스**: payment-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S2·S4·S5] 하자 증빙 사진 업로드 시 확장자 화이트리스트·크기 제한·파일명 변경 적용(S5) / 하자 설명 텍스트 서버 검증 및 출력 인코딩(S2) / 본인 주문만 신청 가능(S4)
- **소분류**: 하자환불 신청
- **예외 처리**: 증빙 누락 → 신청 차단
- **요구사항**: 수령한 리워드의 하자를 사유로 환불을 신청한다
- **우선순위**: MVP
- **입력값**: orderId,하자유형,증빙자료
- **중분류**: 환불
- **처리 내용(기술)**: 하자 유형(불량/파손/표시광고상이) 선택, 증빙(사진·설명) 첨부 후 신청 접수
- **출력값**: 신청 접수 결과
- **트리거 방식**: API 호출

---

## 7. PAYMENT-007 — 하자환불 검토/승인/반려

- **PRD 코드**: FL_B_RF_01_03
- **권한**: 판매자
- **담당 서비스**: payment-service
- **대분류**: 판매자
- **보안/권한 고려사항**: [S2·S4] 승인/반려 사유 텍스트 출력 시 인코딩(S2) / 해당 주문의 판매자 본인 여부 서버 검증, 타 판매자의 환불 건 처리 차단(S4)
- **소분류**: 하자환불 검토/승인/반려
- **예외 처리**: 반려 시 사유 필수 입력
- **요구사항**: 판매자(또는 운영)가 하자환불 신청을 검토해 승인·반려한다
- **우선순위**: MVP
- **입력값**: refundId,승인/반려,사유
- **중분류**: 환불
- **처리 내용(기술)**: 신청 검토 후 승인 시 `pg_payment_key` 기준 토스 취소 API 호출로 환불 실행(반품비 판매자 부담, 정산 시 차감 — PAYMENT-012 연동), `payment_cancellations` 기록, `refund_requests.is_full_refund`는 실제 취소금액이 `payments.amount`와 같은지로 판정해 저장(반품비 차감 등 부분취소면 `false`). 반려 시 사유 기록 → **[신규]** 승인·취소 완료 시 `payment_event_outbox`에 `RefundCompleted`(payload:{triggerType:'DEFECT', isFullRefund:<판정값>}) 적재(반려 시에는 발행하지 않음)
- **출력값**: 처리 결과
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: **[이번 개정]** `RefundCompleted` 이벤트 발행 절차 추가. 하자환불은 부분취소(반품비 차감)가 가능한 유일한 유형이라 `is_full_refund` 판정 로직을 명시(order-service의 "전액환불 건만 쿠폰 복원" 규칙과 직결)

---

## 8. PAYMENT-008 — 발송지연 결제취소 신청

- **PRD 코드**: FL_B_RF_01_04
- **권한**: 구매자
- **담당 서비스**: payment-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4] 본인 주문만 신청 가능, orderId 조작 차단
- **소분류**: 발송지연 결제취소 신청
- **예외 처리**: 신청 시점에 이미 발송 시작됨 → 취소 불가 안내+배송현황 확인 유도
- **요구사항**: 발송 지연을 사유로 결제 취소를 신청한다
- **우선순위**: MVP
- **입력값**: orderId
- **중분류**: 환불
- **처리 내용(기술)**: FS-096 판정 결과(FulfillmentDelayed) 확인 후 취소 신청 접수, 승인 시 `pg_payment_key` 기준 토스 취소 API 전액 취소(환불비 미부과), `payment_cancellations` 기록(`refund_requests.trigger_type=SHIPPING_DELAY`, `is_full_refund=true`). 원 결제수단 환불이 불가한 경우 PAYMENT-005와 동일하게 `alternate_refund_account`를 활용 → **[신규]** 취소 완료 시 `payment_event_outbox`에 `RefundCompleted`(payload:{triggerType:'SHIPPING_DELAY', isFullRefund:true}) 적재
- **출력값**: 신청 접수 결과
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: **[이번 개정]** `RefundCompleted` 이벤트 발행 절차 추가

---

## 9~15. PAYMENT-009 ~ PAYMENT-015

*(정산 관련 기능 — 기존과 동일, 변경 없음. `PaymentFunctionalSpec.md` 기존판 9~15번 항목 그대로 유지)*

---

## 16. PAYMENT-016 — 결제/환불 완료 이벤트 발행(아웃박스 워커) `신규`

- **PRD 코드**: -
- **권한**: 시스템
- **담당 서비스**: payment-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 발행 대상 조회 쿼리 바인딩 변수 사용
- **소분류**: 결제/환불 완료 이벤트 발행
- **예외 처리**: 발행 실패(브로커 장애 등) → `attempt_count` 증가, `last_error` 기록 후 다음 주기 재시도 / 특정 건이 N회 이상 연속 실패 → 알림·수동 개입 대상으로 표시[정책 확인 필요, 재시도 상한값 미정]
- **요구사항**: `payment_event_outbox`에 적재된 `PaymentCompleted`/`RefundCompleted` 이벤트를 최종적 일관성으로 안전하게 발행한다
- **우선순위**: MVP(PAYMENT-002/004/005/007/008이 이 워커에 의존하므로 사실상 필수)
- **입력값**: (내부 폴링 트리거, 파라미터 없음)
- **중분류**: 공통·인프라
- **처리 내용(기술)**: `published_at IS NULL`인 `payment_event_outbox` 행을 주기적으로 폴링 → 메시지 브로커로 발행 → 성공 시 `published_at` 기록. order-service의 `funding_event_outbox`/`FundingEventOutboxWorker`와 동일한 패턴(팀 컨벤션 통일)
- **출력값**: 발행 처리 결과
- **트리거 방식**: 내부 스케줄러(짧은 주기 폴링)
- **검토의견(변경사항)**: 신규 — PAYMENT-002/004/005/007/008이 `PaymentCompleted`/`RefundCompleted`를 발행해야 하는데, 이를 신뢰성 있게(트랜잭션과 함께) 발행할 주체가 기존 문서에 없었음. order-service에는 이미 동일 목적의 `funding_event_outbox`/워커가 있는데 payment-service에는 대응 기능이 누락돼 있었던 것을 확인하고 추가

---

## 17. PAYMENT-017 — 결제-재고만료 충돌 자동 환불 처리 `신규`

- **PRD 코드**: - (13.3.3 파생 예외 케이스)
- **권한**: 시스템
- **담당 서비스**: payment-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 이벤트 소스 검증(order-service가 발행한 이벤트인지 확인)
- **소분류**: 결제-재고만료 충돌 자동 환불
- **예외 처리**: 토스 취소 API 실패 → 재시도·고객센터 안내(PAYMENT-004와 동일)
- **요구사항**: 결제는 성공했으나 order-service의 재고/주문이 이미 만료 처리된 극히 드문 레이스 컨디션 발생 시, 결제금액을 자동으로 전액 환불한다
- **우선순위**: P1 (발생 확률은 낮으나 발생 시 금전 사고로 이어지므로 MVP 범위에서 반드시 설계는 확정하고, 실제 배치 처리 우선순위는 P1으로 둠)
- **입력값**: `PaymentReconciliationRequired` 이벤트(fundingId, paymentId) — order-service가 발행
- **중분류**: 환불
- **처리 내용(기술)**: order-service가 `PaymentCompleted` 처리 중 `fundings.status`가 이미 `PAYMENT_EXPIRED`(ORDER-013 만료 배치가 먼저 실행됨)임을 감지하면 `Funding.status` 전이를 하지 않고 대신 `PaymentReconciliationRequired`를 발행 → payment-service가 이 이벤트를 구독해 PAYMENT-004와 동일하게 `pg_payment_key` 기준 토스 전액 취소 실행, `refund_requests`(`trigger_type` 신규값 필요[정책 확인 필요, 예: `SYSTEM_RECONCILIATION`]) 생성 → 완료 후 구매자에게 "재고 확보 실패로 자동 환불되었습니다" 알림(notification-service 연동, 이 문서 범위 밖)
- **출력값**: 자동 환불 처리 결과
- **트리거 방식**: 이벤트 구독(`PaymentReconciliationRequired`, order-service가 발행)
- **검토의견(변경사항)**: 신규 — order-service의 미결제 만료 배치(ORDER-013)와 payment-service의 결제 승인(PAYMENT-002)이 동시에 실행될 수 있는 레이스 컨디션이 기존 스펙 어디에도 다뤄져 있지 않아 발견 즉시 추가. 발생 빈도는 `payment_expires_at` 정책값(N분)이 충분히 여유 있게 설정되면 극히 낮아지지만, 0으로 만들 수는 없으므로 보상 트랜잭션을 명시적으로 설계함

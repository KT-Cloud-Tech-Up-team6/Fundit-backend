# order-service

> 루트 `CLAUDE.md`(레포 공통 규칙)와 `.claude/rules/`를 전제로, 여기는 order-service에만 해당하는 내용만 다룹니다.

## 이 서비스가 하는 일
재고 원장(inventories), 펀딩 참여(주문), 목표 달성 판정, 참여 취소, 쿠폰(발급/적용/사용/복원)을 소유합니다. (`PRD.md` 2. 도메인/서비스 개요 기준)

리워드/옵션 자체의 정의(가격·이름·이미지 등)는 project-service 소관입니다. project-service가 리워드를 생성·수정하면 이 서비스가 그 이벤트를 구독해 `inventories`(잔여재고 원장)를 동기화하고, project-service는 반대로 이 서비스를 호출해 잔여재고를 조회합니다 — **재고 수치의 단일 진실 공급원(source of truth)은 항상 order-service**입니다.

결제 실행·환불·정산은 payment-service 소관입니다. 이 서비스는 결제 가능한 상태인지(재고·마감 여부)만 검증해 `fundings`를 `PENDING`으로 만들고, 실제 PG 연동과 정산은 payment-service가 이벤트를 구독해 처리합니다. 반대로 **쿠폰의 사용확정/복원(ORDER-015)은 payment-service가 발행하는 결제완료/환불 이벤트를 이 서비스가 구독해서 처리**합니다 — 쿠폰 테이블은 전부 order-service 소유이므로, payment-service가 직접 쓰지 않습니다(아래 "핵심 설계 결정" 참고).

## 먼저 읽을 문서
구현을 시작하기 전에 **`services/order-service/docs/OrderDomainFunctionalSpec.md`(ORDER-001~016)와 `OrderApiSpec.md`를 먼저 읽으세요.** 이 CLAUDE.md는 그 문서들의 핵심만 요약한 것이지 대체하지 않습니다. 이 문서는 **ORDER-001~016 전체**를 구현 대상으로 다룹니다(후순위로 미룬 항목 없음).

> 확인 상태: ERD는 `docs/OrderPaymentErdReview.md`(order/payment 공통 검토 문서)를 거쳐 확정됐고, 기능명세서/API명세서와의 불일치(재고 소유권, 결제실패 시 상태값, 쿠폰 소유권, `PAYMENT-012` 번호 재배치 등)도 정리 완료. 남은 건 아래 "정책값 확인 필요"뿐이며, 이건 코드 구조에 영향을 주지 않는 값/범위 문제라 구현을 막지는 않습니다.

## 로컬 실행
- 앱 포트: `8084` (`application-local.yml`, gitignore 대상 — 커밋하지 않음)
- DB 포트: `5435`
- 최초 한 번: `.env.example`을 `.env`로 복사한 뒤 `docker compose up -d`

```bash
cp services/order-service/.env.example services/order-service/.env
cd services/order-service && docker compose up -d
```

## 구현 순서 권장 (스코프 제한 아님 — 의존관계 때문에 이 순서가 자연스러움)

전부 구현 대상이지만, 아래 순서를 지키지 않으면 뒷 단계가 앞 단계에 막혀서 테스트가 안 됩니다.

1. **ORDER-016(리워드 이벤트 구독/재고 동기화)** — 이게 없으면 `inventories`가 항상 비어 있어 ORDER-003이 재고부족으로만 실패합니다. 브로커 배선이 안 끝났어도 애플리케이션 서비스(`RewardStockSyncService`)와 아웃박스/컨슈머 골격은 먼저 완성해두세요.
2. **ORDER-002/003(주문서 계산/생성)** — 재고 검증·차감의 핵심.
3. **ORDER-004/005(참여 목록/상세), ORDER-014(참여 취소), ORDER-013(미결제 만료 배치)** — 003이 만든 `fundings`를 조회·취소·만료시키는 흐름.
4. **ORDER-006(목표달성 판정)** — 마감 시각 기반 배치, 002~005와 독립적으로 병행 가능.
5. **ORDER-007~012, 015(쿠폰 전체)** — 앞선 주문 흐름과 느슨하게 결합돼 있어 가장 나중에 붙여도 됩니다.
6. **ORDER-001(서포터 활동 목록)** — 순수 조회, 의존성 없음. 아무 때나 끼워 넣어도 무방.

## 도메인 테이블 (스키마 전체)

- `inventories` — 재고 원장의 단일 진실 공급원. `reward_id`(project-service 참조, FK 아님), `available_stock`, `reserved_stock`, `initial_quantity`(project-service `RewardUpdated` 이벤트를 증분으로 반영하기 위한 기준값 — 이 값과 새 `quantity`의 차이만큼만 `available_stock`에 더하고 뺀다), `version`(낙관적 락). `is_limited=false`인 리워드는 이 테이블에 행을 두지 않는다.
- `reward_restock_notify_requests` — 품절 리워드 재입고 알림 신청(ORDER-011). `(reward_id, member_id)` 유니크(중복 신청 방지). **member-service의 `MvpImplementationSummary.md`에도 동일 기능(MEMBER-008, `reward_alerts`)이 별도 제안돼 있어 소유권이 겹친 상태** — 구현 착수 전 PM 확인 필요(아래 "정책값 확인 필요" 참고). 확정 전까지는 이 테이블 기준으로 진행하되, 나중에 정리될 수 있음을 염두에 둘 것.
- `fundings` — 펀딩 참여(주문). `id`(BIGINT, 내부용) / `public_id`(UUID, 외부 노출용 — API 경로·화면의 `orderId`는 전부 이 값), `member_id`(member-service 참조, FK 아님), `project_id`(project-service 참조, FK 아님), `status`(`PENDING`/`FUNDING_IN_PROGRESS`/`CANCELLED_BY_MEMBER`/`PAYMENT_EXPIRED`/`GOAL_FAILED_REFUNDED`/`GOAL_ACHIEVED`/`REFUNDED_AFTER_SUCCESS`), `shipping_fee`(배송비 정책 확정분 스냅샷), `payment_expires_at`(결제 미완료 시 자동 만료 기준 시각).
- `funding_line_items`/`funding_line_item_options` — `fundings`에 속한 자식(같은 애그리거트, 별도 Repository 없음). 리워드/옵션의 가격·이름은 주문 시점 스냅샷으로 저장(project-service가 나중에 값을 바꿔도 과거 주문 내역은 불변).
- `coupons` — 쿠폰 템플릿(발급 주체=플랫폼/메이커). `budget_limit`/`used_budget_amount`(총 할인 예산 추적, `remaining_quantity`만으로는 정률 쿠폰의 예산 소진을 알 수 없어 별도 관리), `remaining_quantity`, `version`(낙관적 락 — 방송 중 선착순 쿠폰 동시 차감 대비), `issue_channel`(`GENERAL`/`LIVE`), `drop_type`(`FIRST_COME`/`MANUAL_DROP`/`WATCH_TIME_AUTO`, LIVE 채널에서만 사용).
- `coupon_issuances` — 회원별 개별 발급 내역. `status`(`AVAILABLE`/`USED`/`EXPIRED`), `used_funding_id`/`used_at`/`restored_at`.
- `funding_coupon_applications` — 주문에 적용된 쿠폰. `(funding_id, coupon_issuance_id)` 유니크 — 한 주문에 여러 행이 붙을 수 있지만(플랫폼쿠폰 1개+메이커쿠폰 1개), 동일 쿠폰을 두 번 적용할 순 없다.

## 핵심 설계 결정 (구현 시 반드시 지킬 것)

### 재고/주문 공통
- **재고 차감은 낙관적 락 + 조건부 UPDATE로만 한다**: `UPDATE inventories SET available_stock = available_stock - :qty, version = version + 1 WHERE reward_id = :id AND version = :v AND available_stock >= :qty`. 애플리케이션 레벨에서 "조회 후 검증 후 저장" 패턴(TOCTOU)으로 짜지 않는다. 갱신 행이 0건이면 재고 부족(`OrderErrorCode.INSUFFICIENT_STOCK`, 409) 또는 동시 수정 충돌이므로 짧게 재시도 후 최종 실패 처리한다.
- **`RewardUpdated` 이벤트는 절대값으로 덮어쓰지 않는다**: project-service가 `quantity`(변경 후 값)를 보내면, `delta = quantity - inventories.initial_quantity`를 계산해 `available_stock += delta`로 반영하고 `initial_quantity`를 새 값으로 갱신한다. `available_stock = quantity`로 그냥 덮어쓰면 이미 판매된 수량이 부활하는 버그가 된다 — 이 로직은 반드시 회귀 테스트(`RewardStockSyncServiceUnitTest`)로 고정할 것.
- **결제 실패는 주문을 취소하지 않는다**: PG 콜백 실패 시 `fundings.status`는 `PENDING`을 유지한다(재시도 허용, 재고 원복하지 않음). 재고를 원복하는 유일한 경로는 ① 참여 취소(`CANCELLED_BY_MEMBER`), ② 미결제 만료 배치(`PAYMENT_EXPIRED`) 두 가지뿐이다.
- **미결제 주문은 배치로만 만료시킨다**: `payment_expires_at`이 지난 `PENDING` 건을 스케줄러가 `PAYMENT_EXPIRED`로 전이시키고 재고를 원복한다. API 호출 경로에서 즉석으로 만료 판정을 하지 않는다(배치가 유일한 전이 주체).
- **주문 금액은 항상 서버에서 재계산한다**: `POST /orders/preview`/`POST /orders` 모두 클라이언트가 보낸 금액을 쓰지 않고, 리워드 단가·배송비·쿠폰 할인을 서버가 매번 다시 계산한다.

### 쿠폰
- **쿠폰 재고 차감도 재고와 동일하게 낙관적 락 + 조건부 UPDATE로 한다**: `coupons.remaining_quantity`를 `version`과 함께 조건부 UPDATE. 특히 LIVE 채널의 `FIRST_COME`/`MANUAL_DROP` 쿠폰은 방송 중 순간적으로 요청이 몰리므로 이 패턴이 필수다.
- **예산 한도(`budget_limit`)와 발급 수량(`remaining_quantity`)은 별개로 관리한다**: 정률 할인 쿠폰은 발급 개수가 남아도 예산이 먼저 소진될 수 있다. 쿠폰 적용(`funding_coupon_applications` 생성) 시 `used_budget_amount`를 함께 갱신한다.
- **한 주문에 쿠폰은 최대 2개, `issuer_type`이 서로 달라야 한다**: 플랫폼쿠폰 1개 + 메이커쿠폰 1개까지만 허용(요구사항정의서 16.3.3). 같은 `issuer_type`을 2개 보내면 `CommonErrorCode.INVALID_INPUT`(400)으로 거부한다.
- **쿠폰 사용확정/복원은 order-service가 자체 이벤트 구독으로 처리한다(ORDER-015)**: payment-service가 발행하는 `PaymentCompleted`/`RefundCompleted` 이벤트를 이 서비스가 구독해 `coupon_issuances.status`를 직접 갱신한다. payment-service가 이 테이블에 직접 쓰지 않도록 한다 — DB-per-service 원칙을 지키기 위한 결정이며, `PaymentDomainFunctionalSpec.md`에도 동일하게 반영돼 있다.
- **번호 혼동 주의**: `기능명세서_전체.xlsx` 원본의 `PAYMENT-012`(쿠폰 사용처리/복원)는 이 서비스의 `ORDER-015`로 이관됐다. `PaymentDomainFunctionalSpec.md`의 현재 `PAYMENT-012`("쿠폰 정산 차감")는 이름은 비슷해도 완전히 다른 기능이니 혼동하지 말 것.

### 인증/서비스 간 통신
- **인증 헤더는 게이트웨이가 검증했다는 전제로만 신뢰한다**: `X-Account-Id`/`X-Account-Role`은 게이트웨이(`platform:gateway-service`)가 JWT를 검증한 뒤 주입한 값이라는 전제로 신뢰하고, order-service가 서명을 다시 검증하지 않는다(member-service `CurrentMemberArgumentResolver`와 동일 패턴). 다만 **소유권 검증(리소스가 진짜 이 계정 것인지)은 반드시 서버에서 한다** — 헤더 값을 신뢰하는 것과 소유권을 대조하는 것은 별개다(`security.md` S4).
- **서비스 간 이벤트는 아웃박스 + Transport 인터페이스로 발행한다**: 메시징 브로커(Kafka/RabbitMQ)가 아직 미확정이므로, project-service의 `RewardEventOutboxWorker`/`RewardEventTransport` 패턴을 그대로 따른다. `FundingGoalFailed`/`FundingSucceeded`(ORDER-006), `FundingCancelledByMember`(ORDER-014)는 같은 트랜잭션에서 아웃박스 테이블에 적재하고, 별도 워커가 `Transport` 구현체로 전달을 시도한다. 브로커가 없는 동안은 `UnconfiguredFundingEventTransport`가 예외를 던져 재시도 상태로 남긴다 — 로깅만 하고 성공으로 취급하지 않는다.
- **project-service용 재고 조회는 동기 API로, 재고 변경 통지는 비동기 이벤트로**: 잔여재고 "조회"(`GET /api/v1/inventories/{rewardId}`)는 project-service가 즉시 필요로 하니 동기 HTTP로 제공하되, 반대 방향(project-service→order-service, 리워드 생성/수정 통지)은 최종적 일관성을 유지하는 이벤트로만 받는다 — 동기 호출로 강결합하지 않는다.

## 에러 코드

도메인 전용 코드는 `OrderErrorCode implements ErrorCode`로 만든다(서비스당 flat enum 1개 — `error-handling.md` 컨벤션). `INVALID_INPUT`/`UNAUTHORIZED`/`FORBIDDEN`/`NOT_FOUND`/`CONFLICT`/`RESOURCE_EXPIRED`/`BUSINESS_RULE_VIOLATION`/`DEPENDENCY_FAILURE`는 이미 `CommonErrorCode`에 있으니 재정의하지 않는다.

전체 매핑은 `docs/OrderApiSpec.md`의 "에러 코드 매핑" 표가 기준이다. 실제로 정의해야 하는 신규 코드:

| 코드 | HTTP | 상황 | 관련 항목 |
| --- | --- | --- | --- |
| `INSUFFICIENT_STOCK` | 409 | 재고보다 많은 수량 주문 | ORDER-003 |
| `ORDER_NOT_CANCELLABLE` | 422 | 마감 후 등 취소 불가 상태에서 취소 시도 | ORDER-014 |
| `COUPON_EXHAUSTED` | 409 | 쿠폰 재고(`remaining_quantity`) 소진 | ORDER-012 |
| `COUPON_NOT_APPLICABLE` | 422 | 쿠폰 최소금액 미달/만료/미보유/1인한도초과 | ORDER-002, ORDER-010, ORDER-012 |
| `COUPON_BUDGET_EXCEEDED` | 422 | 예산 한도 초과 쿠폰 발급 시도 | ORDER-008 |

만료된(`PAYMENT_EXPIRED`) 주문에 대한 조작은 신규 코드를 만들지 않고 `CommonErrorCode.RESOURCE_EXPIRED`(410, "예: 주문 만료" 용도로 이미 정의돼 있음)를 그대로 쓴다. 같은 `issuer_type` 쿠폰 2개 지정처럼 순수 입력 형식 오류는 `CommonErrorCode.INVALID_INPUT`(400)을 그대로 쓴다.

## 이 서비스에서 절대 하지 말아야 할 것

- `RewardUpdated` 이벤트를 받아 `available_stock`을 절대값으로 덮어쓰지 말 것 — 반드시 `initial_quantity` 대비 델타로 반영 (위 "핵심 설계 결정" 참고)
- 재고/쿠폰 차감을 "조회 → 검증 → UPDATE" 여러 단계로 나눠 짜지 말 것 — 조건부 UPDATE 한 번으로 원자적으로 처리
- PG 결제 실패만으로 `fundings.status`를 바꾸거나 재고를 원복하지 말 것 — `PENDING` 유지, 원복은 취소·만료 배치만의 책임
- 클라이언트가 보낸 `X-Account-Id`/금액/재고·쿠폰 수치를 검증 없이 신뢰하지 말 것
- 다른 서비스의 DB 테이블에 직접 접근하지 말 것 — 리워드 정보가 필요하면 project-service를 호출하거나 스냅샷을 쓸 것(예: `funding_line_items`의 리워드명 스냅샷)
- 한 주문에 같은 `issuer_type`의 쿠폰을 2개 이상, 또는 총 3개 이상 적용되게 허용하지 말 것
- `coupon_issuances`/`coupons`의 상태 갱신을 payment-service가 직접 하도록 설계하지 말 것 — 이 서비스가 이벤트를 구독해서 자체적으로 갱신해야 함(DB-per-service 원칙)
- 메시징 브로커가 아직 없다고 이벤트 발행/구독 코드 자체를 생략하지 말 것 — 아웃박스+Transport 인터페이스로 완성해두고, 실제 배선만 나중으로 미룰 것

## 정책값 확인 필요

- **미결제 주문 만료 유예시간(N분)**: `payment_expires_at` 계산 기준값이 아직 정책으로 확정되지 않았습니다(ORDER-013). 구현 착수 전 PM 확인 필요 — 가장 급함, 이게 없으면 ORDER-003 구현이 하드코딩할 숫자가 없어 막힙니다.
- **적립금(포인트) 기능의 MVP 포함 여부**: 요구사항정의서 13.2.1(정의문)엔 "쿠폰·적립금"이라 되어 있는데 13.2.3/13.2.4(실제 정책·기능정의)엔 적립금이 없습니다. payment-service의 `point_transactions` 테이블을 실제로 쓸지 PM 확인 필요(ORDER-002/PAYMENT-001 관련).
- **재입고 알림 신청(ORDER-011) 소유권**: member-service `MvpImplementationSummary.md`의 MEMBER-008과 기능이 중복됩니다. 어느 서비스가 만들지 확정 필요.
- **일반(GENERAL) 쿠폰도 "받기" 능동 클레임(ORDER-012)을 허용할지**: 현재는 라이브 쿠폰(16.6) 전용으로 설계했습니다.
- **하자환불 반품비(PAYMENT-006/007) 처리 방식**: 시스템이 정산에서 차감하는 금액인지, 오프라인으로 처리되는 별개 프로세스인지 — order-service 직접 관련은 아니지만 정산 연동(쿠폰 정산 차감 계산과 유사한 구조) 설계에 영향을 줄 수 있어 참고.
- **`RewardUpdatedEvent`에 변경 전 수량(`previousQuantity`)을 추가할지**: 현재는 order-service가 `initial_quantity`를 자체 보관해 델타를 계산하지만, project-service 쪽에서 이벤트에 `previousQuantity`를 실어 보내는 대안도 있습니다. project-service 담당자와 협의해서 어느 쪽으로 확정할지 결정 필요(순수 기술 결정, PM 불필요).
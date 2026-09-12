# fulfillment-service

> 루트 `CLAUDE.md`(레포 공통 규칙)와 `.claude/rules/`를 전제로, 여기는 fulfillment-service에만 해당하는 내용만 다룹니다.

## 이 서비스가 하는 일
프로젝트 단위 제작·배송 5단계 진행현황 추적(트래커)과, 펀딩(주문) 단위 발송정보(운송장)·수령확인을 소유합니다.

order-service가 펀딩 성립을 판정하고 `FundingSucceeded` 이벤트를 발행하면, 이 서비스는 그걸 구독해 트래커를 만드는 것으로 시작합니다. 반대 방향으로, payment-service는 이 서비스를 내부 API로 호출해 특정 funding의 발송지연 여부·수령확인 시각을 조회합니다 — **배송 상태의 단일 진실 공급원(source of truth)은 항상 fulfillment-service**이고, payment-service의 `ShippingStatusClient`/`StubShippingStatusClient`(지금은 항상 "미발송"만 반환하는 스텁)가 이 서비스가 생기길 기다리며 그 자리를 메우고 있습니다.

리워드·재고·펀딩 성립 판정 자체는 order-service 소관이고, 결제·환불·정산은 payment-service 소관입니다. 이 서비스는 그 이후 단계(제작 착수 ~ 수령확인)만 다룹니다.

## ⚠️ 먼저 확인할 것 — 서비스명 불일치
`docs/PRD.md`의 "2. 도메인/서비스 개요" 표, `.claude/rules/config-convention.md`의 로컬 포트 표, payment-service 코드 주석(`ShippingStatusClient`, `StubShippingStatusClient`, `ShippingCompletionListener`)은 전부 이 도메인을 **`shipping-service`**라고 부릅니다. 반면 이번에 작성한 설계 문서(`FulfillmentERD.md`, `fullfillmentFunctionalSpec.md`, `FullfillmentApiSPec.md`)는 **`fulfillment-service`**라는 이름을 씁니다. 같은 서비스를 가리키는 것으로 보이지만 이름이 다릅니다.

- 이 CLAUDE.md는 설계 문서와 맞추기 위해 일단 `fulfillment-service`로 진행합니다.
- **구현 착수 전에** `docs/PRD.md`와 `config-convention.md`를 `fulfillment-service`로 통일할지, 반대로 이 서비스를 `shipping-service`로 리네임할지 결정하고 넘어가세요. 결정이 없으면 payment-service 담당자가 `StubShippingStatusClient`를 실제 클라이언트로 교체할 때 어떤 서비스명/포트를 호출해야 하는지 헷갈립니다.
- 포트는 이름과 무관하게 `config-convention.md`가 이미 이 도메인 몫으로 비워둔 슬롯(앱 `8087`, DB `5438`)을 그대로 씁니다 — 아래 "로컬 실행" 참고.

## 먼저 읽을 문서
구현을 시작하기 전에 **`FulfillmentERD.md`(ERD·DDL 검토·수정본), `fullfillmentFunctionalSpec.md`(FULFILLMENT-001~010), `FullfillmentApiSPec.md`를 먼저 읽으세요.** 이 CLAUDE.md는 그 문서들의 핵심만 요약한 것이지 대체하지 않습니다. **FULFILLMENT-001~010 전체**가 구현 대상입니다(후순위로 미룬 항목 없음 — 우선순위는 전부 MVP).

> 확인 상태: ERD·기능명세서·API명세서 모두 2차 검토를 거쳐 자체 발견한 설계 결함 2건(아래 "핵심 설계 결정" 1·2번)을 수정 완료. 남은 건 아래 "정책값 확인 필요"뿐이며, 코드 구조에 영향을 주지 않는 값 문제라 구현을 막지는 않습니다.

## 로컬 실행
- 앱 포트: `8087` (`application-local.yml`, gitignore 대상 — 커밋하지 않음. `config-convention.md`의 `shipping-service` 슬롯 재사용)
- DB 포트: `5438`
- 최초 한 번: `.env.example`을 `.env`로 복사한 뒤 `docker compose up -d`

```bash
cp services/fulfillment-service/.env.example services/fulfillment-service/.env
cd services/fulfillment-service && docker compose up -d
```

## 구현 순서 권장 (스코프 제한 아님 — 의존관계 때문에 이 순서가 자연스러움)

전부 구현 대상이지만, 아래 순서를 지키지 않으면 뒷 단계가 앞 단계에 막혀서 테스트가 안 됩니다.

1. **DB 마이그레이션** — `FulfillmentERD.md` 최종 DDL 기준 4개 테이블 + 인덱스 전체를 Flyway로 먼저 올리세요.
2. **FULFILLMENT-001(트래커 초기화, `FundingSucceeded` 이벤트 구독)** — 이게 없으면 `fulfillment_trackers`가 항상 비어 있어 아래 모든 API가 대상 트래커를 못 찾고 404만 납니다. 브로커 배선이 안 끝났어도 애플리케이션 서비스와 컨슈머 골격은 먼저 완성해두세요(order-service ORDER-016과 같은 성격).
3. **FULFILLMENT-002/003/005(판매자 진행현황 등록, 공통 조회, 일정변경)** — 트래커가 생긴 다음 붙는 가장 기본적인 읽기/쓰기 흐름.
4. **FULFILLMENT-004(미등록 알림 배치)** — 002가 갱신하는 `last_updated_at`에 의존하므로 002 이후.
5. **FULFILLMENT-006(발송정보 등록), FULFILLMENT-003의 펀딩 단위 조회** — `shipments`는 여기서 처음 생성됩니다(지연 생성 — 아래 "핵심 설계 결정" 4번 참고).
6. **FULFILLMENT-007(배송완료 목업 배치)** — 006이 만든 `SHIPPED` 상태에 의존.
7. **FULFILLMENT-009/010(수령확인, 자동확정 배치)** — 007이 만든 `DELIVERED` 상태에 의존.
8. **FULFILLMENT-008(payment-service 연동 내부 API)** — 가장 마지막. `shipments`가 없는 케이스(미발송)를 order-service 내부 API로 보강해야 하므로 1~7번이 전부 끝난 뒤 붙이는 게 테스트하기 편합니다.

## 도메인 테이블 (스키마 전체)

- `fulfillment_trackers` — 제작·배송 진행상황의 단일 행. **프로젝트당 1행, 참여자 전원이 공유**(생산 공정은 구매자마다 다르지 않음). `project_id`(catalog-service 참조, FK 아님, UNIQUE), `current_stage`(5단계 CHECK: `PRODUCTION_START`→`MANUFACTURING`→`INSPECTION`→`SHIPPING_OUT`→`DELIVERY`, 순방향 전이만 허용), `last_updated_at`(1주 미갱신 알림 배치의 기준 시각).
- `fulfillment_stage_details` — 판매자가 주기적으로 등록하는 단계별 예상일정·상세내용. **append-only**(이력 보존, 수정이 아니라 항상 새 행 추가). `tracker_id` FK.
- `fulfillment_schedule_changes` — 일정 변경·지연사유 이력. `reason_type`은 화이트리스트 CHECK(`START_DELAY`/`STOCK_SHORTAGE`/`INSPECTION_DELAY`/`SHIPPING_DELAY`/`OTHER`). `tracker_id` FK.
- `shipments` — 펀딩(주문) 단위 발송·수령 정보. **판매자가 발송 등록을 하기 전까지는 행 자체가 없음**(지연 생성). `funding_id`(order-service 참조, FK 아님, UNIQUE — 재배송/분할배송 범위 밖 가정), `project_id`(같은 DB의 `fulfillment_trackers.project_id` 참조라 **FK 있음**), `status`(`PREPARING`→`SHIPPED`→`DELIVERED`→`RECEIPT_CONFIRMED` CHECK), `delivered_at`/`receipt_confirmed_at`/`receipt_auto_confirmed`.

## 핵심 설계 결정

- **트래커 생성은 오직 이벤트 구독 하나뿐**: `fulfillment_trackers`를 만드는 API는 없습니다. `FundingSucceeded` 이벤트 구독(FULFILLMENT-001)이 유일한 생성 경로입니다. 이 배선이 끝나지 않으면 나머지 전부가 막힙니다.
- **DELIVERY 전이는 판매자 수동 조작만 허용**: 초안에는 "프로젝트의 모든 funding이 발송되면 자동으로 DELIVERY로 전이"하는 로직이 있었지만 제거했습니다. fulfillment-service는 프로젝트의 전체 funding 개수를 알 방법이 없어(그건 order-service 소관 데이터) "모두 발송됨"을 판정할 수 없었습니다. 모든 단계 전이(DELIVERY 포함)는 FULFILLMENT-002의 명시적 API 호출로만 이루어집니다.
- **미발송 funding의 projectId는 order-service에 물어봐서 구한다**: FULFILLMENT-008(payment-service 연동 내부 API)이 발송지연 여부를 판정하려면 그 funding의 `projectId`가 필요한데, 발송 전에는 `shipments`(`project_id`를 들고 있는 유일한 로컬 테이블) 행 자체가 없습니다. 이 경우 order-service의 기존 내부 API `GET /internal/fundings/{fundingId}`(payment-service `OrderFundingClient`가 이미 호출 중인 것과 동일 엔드포인트)를 호출해 `projectId`를 그때그때 조회하세요. "미발송이니 그냥 빈 값 응답"으로 넘기면 PAYMENT-008이 가장 궁금해하는 케이스(미발송+지연 여부)를 판정 못 하는 설계가 됩니다.
- **`shipments`는 지연 생성(lazy)**: 판매자가 발송 등록(FULFILLMENT-006)을 하기 전까지 해당 funding의 `shipments` 행은 존재하지 않습니다. 조회 API(FULFILLMENT-003 펀딩 단위)는 행이 없으면 에러가 아니라 `status: PREPARING`으로 응답하세요.
- **택배사 실연동 없음, 전부 목업 배치**: 요구사항정의서 8.3.3에 따라 실제 택배사 배송추적 API 연동은 이번 범위 밖입니다. "발송" 이후 상태 전이(배송완료 FULFILLMENT-007, 자동확정 FULFILLMENT-010)는 전부 경과 시간 기준 배치로 처리합니다. 실제 연동 시점에는 FULFILLMENT-007이 웹훅 수신 로직으로 대체될 자리입니다.
- **타 서비스 참조는 FK 없음, 같은 DB 참조는 FK 있음**: `fulfillment_trackers.project_id`(catalog-service 참조)와 `shipments.funding_id`(order-service 참조)는 FK를 걸지 않습니다. 반면 `shipments.project_id → fulfillment_trackers.project_id`는 같은 DB 안이므로 FK로 정합성을 보장합니다 — MSA 경계와 무관하게 "같은 DB냐"가 FK 여부의 기준입니다.

## 에러 코드

도메인 전용 코드는 `FulfillmentErrorCode implements ErrorCode`로 만드세요(서비스당 flat enum 1개 — `error-handling.md` 컨벤션). `INVALID_INPUT`/`UNAUTHORIZED`/`FORBIDDEN`/`NOT_FOUND`/`CONFLICT`/`BUSINESS_RULE_VIOLATION`/`DEPENDENCY_FAILURE`는 이미 `CommonErrorCode`에 있으니 재정의하지 않습니다.

전체 매핑은 `FullfillmentApiSPec.md`의 "에러 코드 매핑" 표가 기준입니다. 실제로 정의해야 하는 신규 코드:

| 코드 | HTTP | 상황 | 관련 항목 |
| --- | --- | --- | --- |
| `ALREADY_SHIPPED` | 409 | 이미 `SHIPPED` 이상 상태인 건에 발송 재등록 시도 | FULFILLMENT-006 |
| `NOT_YET_DELIVERED` | 422 | 배송완료 전 수령확인 시도 | FULFILLMENT-009 |
| `INVALID_STAGE_TRANSITION` | 422 | 단계 역방향 전이 시도 | FULFILLMENT-002 |

order-service(funding-project 소유관계, projectId 조회 등) 호출 실패는 신규 코드를 만들지 않고 `CommonErrorCode.DEPENDENCY_FAILURE`(503)를 그대로 씁니다.

## 이 서비스에서 절대 하지 말아야 할 것

- `fulfillment_trackers`를 API로 직접 생성하는 엔드포인트를 만들지 말 것 — 생성은 오직 `FundingSucceeded` 이벤트 구독뿐.
- "프로젝트의 funding이 전부 발송됐는지" 같은, order-service만 아는 정보를 이 서비스가 스스로 판단하려 하지 말 것 — 필요하면 order-service를 호출할 것.
- `shipments` 행이 없는 걸 에러로 취급하지 말 것 — 아직 발송 전 상태(`PREPARING`)로 정상 응답할 것.
- 단계(`stage`) 값을 역방향으로 전이시키거나, CHECK 제약 없이 임의 문자열을 저장하지 말 것.
- 클라이언트가 보낸 `X-Account-Id`만으로 소유권을 신뢰하지 말 것 — 판매자 API는 프로젝트 소유권을, 구매자 API는 funding 소유권을 서버에서 검증할 것(`security.md` S4). 판매자 발송/조회 API는 경로에 `projectId`가 있으므로 프로젝트 소유권만 먼저 확인하고, funding이 실제 그 프로젝트 소속인지는 필요시 order-service로 교차 검증할 것.
- 택배사 API가 아직 없다고 발송완료·자동확정 배치 자체를 생략하지 말 것 — 목업 배치로 완성해두고 실제 연동만 나중으로 미룰 것.

## 정책값 확인 필요

- **FULFILLMENT-004** 미등록 알림 기준 "N일"(초안 7일).
- **FULFILLMENT-007** 배송완료 목업 처리 기준 "N영업일"(초안 3영업일).
- **FULFILLMENT-010** 미확인 자동확정 기준 "N일"(초안 7일).
- **`shipments.funding_id` UNIQUE 제약**: 재배송/분할배송(하자 교환 등) 지원이 필요해지면 이 제약부터 완화해야 합니다. 현재는 범위 밖으로 가정.
- **서비스명(`fulfillment-service` vs `shipping-service`)**: 위 "먼저 확인할 것" 참고 — 이건 정책값이 아니라 구현 착수 전 반드시 결정해야 하는 항목입니다.
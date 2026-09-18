# 프로젝트 도메인 추후 진행 사항

> 작성일: 2026-09-18
> 목적: 지금 바로 진행하지 않고 별도 확인·선행 작업이 필요한 항목을 기록해 놓치지 않도록 한다.
> 각 항목이 실제로 진행 가능해지면 이슈로 옮기고 이 문서에서는 상태만 "진행중/완료"로 갱신한다.

## 1. 리워드 배송비 · 예상 발송일 (마이그레이션 필요)

- **배경**: 프론트엔드 요청(V02) — 리워드 상세페이지에 배송비, 예상 발송일 표시 필요. 현재 `rewards` 테이블에 해당 컬럼 자체가 없음.
- **필요 작업**:
  - `V13__add_reward_shipping_info.sql` 신규 마이그레이션 (`shipping_fee BIGINT`, `estimated_delivery_date` 등 — 컬럼명/타입/nullable 여부 확정 필요)
  - `Reward` 도메인 / `RewardJpaEntity` / `RewardMapper` / 생성·수정 요청 DTO / 응답 DTO 반영
- **선행 확인 필요**: 배송비가 리워드마다 다른지 프로젝트 전체 공통인지, 예상 발송일이 고정 날짜인지 "펀딩 종료 후 N일" 같은 상대값인지 — 기획 확인 후 컬럼 설계 확정
- **상태**: 미착수

## 2. 리워드 실제 재고 연동 (order-service 의존)

- **배경**: `RewardConsumerResponse.soldOut`/`remainingStock` 필드는 이미 응답에 존재하지만, 실제 값을 채우는 `InventoryQueryClient`가 `NoopInventoryQueryClient`로만 구현되어 있어 항상 빈 값(null)을 반환했다. 이 스텁은 "order-service가 아직 스캐폴딩되지 않았을 때" 만든 placeholder였는데, order-service가 이미 존재하고 `GET /api/v1/inventories/{rewardId}`도 이미 노출돼 있어 바로 연동 가능했다.
- **완료된 작업**:
  - order-service: `GET /api/v1/inventories/{rewardId}`를 `InternalEndpointConfig`에 등록해 `X-Internal-Api-Key` 검증 추가(기존엔 게이트웨이 라우트 제외만 있고 2차 방어선이 빠져 있던 보안 공백)
  - project-service: [`HttpInventoryQueryClient`](../src/main/java/com/fundit/project/infrastructure/inventory/HttpInventoryQueryClient.java)로 실제 HTTP 연동 — [`StubInventoryQueryClient`](../src/main/java/com/fundit/project/infrastructure/inventory/StubInventoryQueryClient.java)(구 Noop)는 `order.integration.inventory-client.mode=stub`일 때만 활성화, dev/prod 기본값은 `http`. 실패 시 `DependencyFailureException` 대신 빈 값으로 degrade(soldOut 표시는 best-effort)
- **상태**: 완료

## 3. 리워드 수량 "무제한" 표기 계약 정리 (FE 협의 필요)

- **배경**: PM 답변에서 "수량을 -1로 하면 무제한"이라고 언급했으나, 기존 백엔드 계약은 `isLimited: false` + `quantity` 생략(null)으로 무제한을 표현했다 (`Reward.validateQuantity`: `isLimited ? quantity!=null && quantity>=0 : quantity==null`).
- **완료된 작업**: FE 정식 협의 전까지 우선 두 계약을 병행 허용하기로 함 — 정식 계약(`isLimited:false` + `quantity` 생략/`null`)은 그대로 canonical로 유지하고, `quantity: -1`을 별칭으로 추가 허용한다. `RewardController`가 `isLimited`가 `true`가 아닐 때만 `quantity:-1`을 `isLimited:false`+`quantity:null`로 정규화하고, `isLimited:true`와 함께 오면 정규화하지 않아 기존 도메인 검증(`quantity>=0`)이 그대로 `INVALID_REWARD_QUANTITY`로 거부한다. 응답은 항상 canonical 형태로만 내려간다. `ProjectDomainApiSpec.md`에 반영.
- **상태**: 완료(계약 자체가 FE와 정식 확정되면 이 문서 갱신)

## 4. cross-service ID(Long ↔ UUID) 불일치 (order/payment/fulfillment 스키마 변경 필요)

- **배경**: project-service는 대외로 `Project.publicId`(UUID)만 노출하는데(내부 PK는 `Project.id`, Long), order-service/fulfillment-service는 여전히 project-service의 **Long 내부 PK**를 `projectId`로 그대로 요구한다. payment-service도 order-service의 UUID `orderId`(`Funding.publicId`)가 아니라 order-service **내부 PK인 Long `fundingId`**를 받는다. 즉 조회(UUID) → 주문/결제/배송(Long) 경계에서 클라이언트가 서로 다른 타입의 ID를 들고 다녀야 하는 상태.
- **실태 (2026-09-18 조사 기준)**:
  | 서비스 | 필드 | 타입 |
  |---|---|---|
  | project-service | `projectId`(대외 노출) | UUID |
  | order-service | 요청/엔티티/응답의 `projectId` | **Long** (project-service 내부 PK 그대로 요구) |
  | order-service | `orderId`(`Funding.publicId`) | UUID |
  | payment-service | `fundingId` | **Long** (order-service 내부 PK, `orderId` UUID 아님) |
  | fulfillment-service | `projectId`, `fundingId` (path/엔티티/응답 전부) | **Long** |
  | member-service | Follow `sellerId` | UUID (project-service와 일치, 문제 없음) |
- **영향**: 프론트가 프로젝트 조회 응답의 `projectId`(UUID)를 그대로 주문 생성에 넘기면 실패한다. 주문 생성 응답의 UUID `orderId`만으로는 결제(Long `fundingId` 필요)를 호출할 수 없다.
- **필요 작업**: project-service 단독으로 해결할 수 없고 **order-service/payment-service/fulfillment-service 세 곳의 스키마·API 계약을 함께 바꿔야 하는 cross-service 작업**이다. 각 서비스에서 기존 마이그레이션은 수정 금지이므로 새 UUID 컬럼 추가 → dual-write/백필 → 컷오버 순서의 다단계 마이그레이션이 필요. project-service 쪽 작업(있다면)만 이 문서에서 트래킹하고, 전체 진행 순서·우선순위는 별도 cross-service 문서/이슈로 관리한다.
- **상태**: 미착수 — cross-service 논의 필요

# 프로젝트 도메인 추후 진행 사항

> 작성일: 2026-09-18
> 목적: 지금 바로 진행하지 않고 별도 확인·선행 작업이 필요한 항목을 기록해 놓치지 않도록 한다.
> 각 항목이 실제로 진행 가능해지면 이슈로 옮기고 이 문서에서는 상태만 "진행중/완료"로 갱신한다.

## 1. 리워드 배송비 · 예상 발송일 (마이그레이션 필요)

- **배경**: 프론트엔드 요청(V02) — 리워드 상세페이지에 배송비, 예상 발송일 표시 필요. 기존 `rewards` 테이블에 해당 컬럼 자체가 없었음.
- **완료된 작업**: 기획 확인 전까지 유연한 기본 스키마로 우선 구현(`V13__add_reward_shipping_info.sql`) — `shipping_fee BIGINT`(리워드별 nullable, NULL=미설정), `estimated_delivery_days INTEGER`(펀딩 종료 후 N일, 상대값). `Reward` 도메인(`changeShippingInfo`류 없이 `create`/`changeBasicInfo`에 통합, `validateShippingInfo`로 0 이상만 허용) / `RewardJpaEntity` / `RewardMapper` / `RewardCreateRequest`/`RewardUpdateRequest` / `RewardResponse`/`RewardConsumerResponse` 전부 반영.
- **선행 확인 필요(여전히 남음)**: 배송비가 리워드마다 다른지 프로젝트 전체 공통인지, 예상 발송일이 고정 날짜인지 상대값인지는 여전히 기획 미확정 — 답이 다르게 나오면 후속 마이그레이션(새 컬럼 추가, 기존 컬럼은 수정 금지 원칙 유지)으로 조정.
- **상태**: 1차 구현 완료, 기획 확정 시 스키마 재조정 가능성 있음

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
- **실태 (2026-09-18, #69 컷오버 후)**:
  | 서비스 | 필드 | 타입 |
  |---|---|---|
  | project-service | `projectId`(대외 노출) | UUID (변경 없음) |
  | order-service | v2 `projectId` / 도메인 `Funding.projectId` | UUID (project-service `publicId`) |
  | order-service | `orderId`(`Funding.publicId`) | UUID |
  | payment-service | v2 `fundingId` / 도메인 `Payment.fundingId` | UUID (order-service `orderId`) |
  | fulfillment-service | v2 `projectId` / `fundingId` | UUID / UUID |
  | member-service | Follow `sellerId` | UUID (문제 없음) |
- **영향(해소)**: 프론트가 프로젝트 조회 응답의 `projectId`(UUID)를 `POST /api/v2/orders`에 그대로 넘기고, 주문 응답의 `orderId`를 `POST /api/v2/payments`의 `fundingId`로 그대로 넘긴다. v1 Long 경로는 어댑터로 유지한다.
- **완료된 작업**: order/payment/fulfillment 공개 API `/api/v2/` UUID 계약, v1 Long 어댑터, DB UUID 컬럼 추가(Expand) + order `FundingProjectPublicIdBackfillRunner`. project-service 코드 변경 없음 — 공개 API는 원래 UUID만 노출. 소유권/제목 조회는 v2가 `GET /api/v1/projects/{publicId}`를, v1/Kafka Long 해석만 `GET /internal/projects/{longPk}`를 쓴다.
- **후속(이 이슈 범위 밖)**: 레거시 Long 컬럼 DROP(Contract), Kafka `fundingId` Long 제거(지금은 v1 페이로드에 `orderId`/`projectPublicId` UUID 필드 추가).
- **상태**: 완료

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

- **배경**: `RewardConsumerResponse.soldOut`/`remainingStock` 필드는 이미 응답에 존재하지만, 실제 값을 채우는 `InventoryQueryClient`가 [`NoopInventoryQueryClient`](../src/main/java/com/fundit/project/infrastructure/inventory/NoopInventoryQueryClient.java)로만 구현되어 있어 항상 빈 값(null)을 반환한다. 이 스텁은 "order-service가 아직 스캐폴딩되지 않았을 때" 만든 placeholder인데, 현재는 order-service가 이미 존재한다.
- **결과적으로 지금은**: 얼리버드 리워드 재고가 소진돼도 응답상 `soldOut`이 항상 `false`로 나가 소비자 화면에서 품절 처리가 실제로 동작하지 않음.
- **필요 작업**:
  - order-service에 재고(잔여 수량) 조회용 API(내부 엔드포인트)가 있는지 확인 — 없다면 order-service 쪽에 먼저 추가 요청
  - 있다면 `InventoryQueryClient`의 실제 HTTP 구현체 작성 후 `NoopInventoryQueryClient` 대체, 타임아웃 설정(서비스 간 동기 호출 규칙 준수)
- **선행 확인 필요**: order-service 담당(본인 소유 도메인이면 order-service 쪽 엔드포인트 존재 여부부터 확인)
- **상태**: 미착수

## 3. 리워드 수량 "무제한" 표기 계약 정리 (FE 협의 필요)

- **배경**: PM 답변에서 "수량을 -1로 하면 무제한"이라고 언급했으나, 현재 백엔드 계약은 `isLimited: false` + `quantity` 생략(null)으로 무제한을 표현한다 (`Reward.validateQuantity`: `isLimited ? quantity!=null && quantity>=0 : quantity==null`).
- **확인 필요**: 프론트가 실제로 API 요청에 `quantity: -1`을 리터럴로 보내려는 것인지(계약 변경 필요), 아니면 기획 문서상의 개념 설명일 뿐이고 실제 연동은 `isLimited:false`로 충분한지.
- **필요 작업(계약 변경이 필요할 경우)**: `RewardCreateRequest`/`RewardUpdateRequest`에서 `quantity: -1`을 무제한으로 해석하도록 검증 로직 변경, 또는 API 스펙 문서에 "무제한 시 quantity 생략" 규칙을 명시해 FE와 합의
- **상태**: FE 협의 대기

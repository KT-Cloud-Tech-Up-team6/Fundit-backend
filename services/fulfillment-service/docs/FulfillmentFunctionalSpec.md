# fulfillment-service 기능명세서 (FULFILLMENT-001~010)
 
## 1. FULFILLMENT-001 — 트래커 초기화(펀딩 성립 이벤트 구독) `신규`
 
- **PRD 코드**: -
- **권한**: 시스템
- **담당 서비스**: fulfillment-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 이벤트 페이로드의 `projectId` 타입·범위 검증 후 바인딩 변수로 저장
- **소분류**: 트래커 초기화
- **예외 처리**: 이미 트래커가 있는 프로젝트에 대해 중복 이벤트 수신 시 무시(idempotent, `uq_fulfillment_trackers_project` 위반을 정상 케이스로 처리)
- **요구사항**: 펀딩이 목표를 달성해 성립되면 해당 프로젝트의 제작·배송 진행 현황 추적을 시작한다
- **우선순위**: MVP
- **입력값**: `FundingSucceeded` 이벤트(projectId)
- **중분류**: 제작/배송
- **처리 내용(기술)**: order-service ORDER-006이 발행하는 `FundingSucceeded` 이벤트를 구독해 `fulfillment_trackers(project_id, current_stage='PRODUCTION_START')`를 생성. 같은 `project_id`로 이미 트래커가 있으면 아무 것도 하지 않음(idempotent)
- **출력값**: 생성된 트래커
- **트리거 방식**: 이벤트 구독(`FundingSucceeded`)
- **검토의견(변경사항)**: 신규 — 이 초기화 경로가 없으면 FULFILLMENT-002(판매자 진행현황 등록) 이하 모든 기능이 대상 트래커를 찾지 못해 실패한다(order-service ORDER-016과 동일한 성격의 누락).
---
 
## 2. FULFILLMENT-002 — 제작·배송 진행 현황 등록/수정 (판매자)
 
- **PRD 코드**: FL_S_DL_01_01
- **권한**: 판매자
- **담당 서비스**: fulfillment-service
- **대분류**: 판매자
- **보안/권한 고려사항**: [S1·S4] 본인 소유 프로젝트에만 등록 가능(프로젝트 소유권은 project-service 조회 필요[가정] 또는 X-Account-Id·프로젝트 판매자 스냅샷 대조), 단계·예상일정 등 입력값은 바인딩 변수 사용
- **소분류**: 진행 현황 등록/수정
- **예외 처리**: 존재하지 않는 트래커(FULFILLMENT-001 미실행) → 404 / 이전 단계로 역행 시도 → 422(단계는 순방향 전이만 허용)
- **요구사항**: 판매자가 제작·배송의 현재 단계, 단계별 예상일정, 상세 진행 내용을 등록·수정한다
- **우선순위**: MVP
- **입력값**: projectId, (선택)전이할 단계, 단계별 예상 시작일·완료일, 상세 진행 내용(텍스트)
- **중분류**: 제작/배송
- **처리 내용(기술)**: 단계 전환 시 `fulfillment_trackers.current_stage` 갱신(순방향만 허용, 5단계 CHECK 재확인). 예상일정·상세내용은 `fulfillment_stage_details`에 append(이력 보존) 후 `fulfillment_trackers.last_updated_at` 갱신(1주 강제 정책의 기준 시각)
- **출력값**: 갱신된 진행 현황(현재 단계, 최신 상세내용)
- **트리거 방식**: API 호출
---
 
## 3. FULFILLMENT-003 — 제작·배송 진행 현황 조회 (공통)
 
- **PRD 코드**: FL_S_DL_01_01 / FL_B_MY_02_01
- **권한**: 공통(판매자 관리 화면·구매자 조회 화면이 같은 데이터를 봄)
- **담당 서비스**: fulfillment-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S4] 구매자 조회는 본인이 참여한 프로젝트인지 order-service 소유권 확인이 선행되어야 함[가정 — fulfillment-service 자체는 projectId 기준 공개 정보라 별도 인가 없이 조회 가능하게 설계하고, "내가 참여한 프로젝트인지"는 상위 화면(마이페이지)에서 걸러진 상태로 진입한다고 가정]. 단, 펀딩별 배송정보(운송장 등)는 참여자 본인 것만 노출해야 하므로 funding 소유자와 요청자 계정 대조(S4)가 별도로 필요
- **소분류**: 진행 현황 조회
- **예외 처리**: 트래커 없음(아직 미성립 프로젝트 등) → 404 / 로드 실패 → 안내+재시도(프론트 처리)
- **요구사항**: (프로젝트 단위) 현재 단계, 단계별 상태(진행 전·진행 중·완료)·예상일정·최신 상세내용·일정 변경 이력과, (펀딩 단위) 발송정보(택배사·운송장번호·배송상태)를 확인한다
- **우선순위**: MVP
- **입력값**: projectId, (펀딩별 배송정보 조회 시) fundingId
- **중분류**: 제작/배송
- **처리 내용(기술)**: 프로젝트 단위 조회는 `fulfillment_trackers.current_stage` 기준으로 5단계 각각의 상태(완료/진행중/진행전) 계산, 단계별 최신 `fulfillment_stage_details` 1건(`updated_at DESC LIMIT 1`) 조회, `fulfillment_schedule_changes` 이력 함께 반환. `last_updated_at`이 1주 이상 경과했으면 "업데이트 예정" 플래그 포함(14.3.3). 펀딩 단위 조회는 해당 `shipments` 1건(택배사·운송장번호·`status`·`shippedAt`·`deliveredAt`·`receiptConfirmedAt`)을 반환하며, 아직 발송 전(레코드 없음)이면 `status: PREPARING`으로 응답(14.3.4 "발송 정보 조회")
- **출력값**: (프로젝트) 단계별 상태·예상일정·상세내용, 일정 변경 이력, 최종 갱신 시각 / (펀딩) 발송정보·배송상태·수령확인 가능 여부
- **트리거 방식**: API 호출
---
 
## 4. FULFILLMENT-004 — 상세 진행 내용 미등록 알림 `신규`
 
- **PRD 코드**: -
- **권한**: 시스템
- **담당 서비스**: fulfillment-service
- **대분류**: 판매자
- **보안/권한 고려사항**: [S1] 배치 쿼리 바인딩 변수 사용
- **소분류**: 미등록 알림
- **예외 처리**: -
- **요구사항**: 판매자가 정해진 주기(1주)마다 상세 진행 내용을 등록하지 않으면 등록을 요청하는 알림을 보낸다
- **우선순위**: P1
- **입력값**: (배치 트리거, 파라미터 없음)
- **중분류**: 제작/배송
- **처리 내용(기술)**: `idx_fulfillment_trackers_stale` 인덱스로 `current_stage <> 'DELIVERY'`이면서 `last_updated_at`이 7일[정책값, 확정 필요] 이상 경과한 트래커를 조회해 판매자에게 업데이트 요청 알림 이벤트 발행(notification-service 구독, 이 문서 범위 밖)
- **출력값**: 알림 대상 건수
- **트리거 방식**: 스케줄러
- **검토의견(변경사항)**: 신규 — 요구사항정의서 8.1.3 "미등록 시 업데이트 요청 알림 발송"에 대응하는 배치가 원본 기능명세서에 없었음
---
 
## 5. FULFILLMENT-005 — 일정 변경·지연사유 등록 (판매자)
 
- **PRD 코드**: FL_S_DL_02_01
- **권한**: 판매자
- **담당 서비스**: fulfillment-service
- **대분류**: 판매자
- **보안/권한 고려사항**: [S2·S4] 자유입력 지연사유(`reason_detail`)는 구매자 화면에 노출되므로 출력 인코딩 적용(S2) / 본인 소유 프로젝트만 등록 가능(S4)
- **소분류**: 일정 변경·지연사유 등록
- **예외 처리**: `reason_type`이 화이트리스트(4개 선택항목+OTHER) 밖이면 400
- **요구사항**: 기존 안내한 일정이 변경될 경우 사유와 새 예상일정을 등록한다
- **우선순위**: MVP
- **입력값**: projectId, stage, reason_type(선택 4항목 또는 OTHER), reason_detail(자유입력, OTHER일 때 필수), new_planned_date
- **중분류**: 제작/배송
- **처리 내용(기술)**: 대상 단계의 기존 예상일정(`fulfillment_stage_details` 최신 값)을 old_planned_date로 스냅샷, `fulfillment_schedule_changes`에 변경 이력 저장, 해당 단계의 예상일정을 새 값으로 갱신. 저장과 동시에 구매자 알림 이벤트 발행(14.3.3)
- **출력값**: 등록된 일정 변경 이력
- **트리거 방식**: API 호출
---
 
## 6. FULFILLMENT-006 — 발송정보 등록(발송 처리) (판매자)
 
- **PRD 코드**: FL_S_DL_03_01
- **권한**: 판매자
- **담당 서비스**: fulfillment-service
- **대분류**: 판매자
- **보안/권한 고려사항**: [S1·S4] 택배사·운송장번호는 바인딩 변수로 저장 / 본인 소유 프로젝트(path의 projectId)에 속한 funding에 대해서만 등록 가능 — projectId가 경로에 있어 프로젝트 소유권만 확인하면 되고(project-service 조회 또는 판매자 스냅샷 대조), funding이 실제로 그 projectId에 속하는지는 order-service 내부 API로 교차 검증[가정]
- **소분류**: 발송정보 등록
- **예외 처리**: 이미 `SHIPPED` 이상 상태인 건에 재등록 시도 → 409 / 필수값(택배사, 운송장번호) 누락 → 400
- **요구사항**: 제작 완료 이후 택배사·운송장 번호를 등록하고 발송 완료 상태로 전환한다
- **우선순위**: MVP
- **입력값**: projectId, fundingId, carrier, trackingNumber
- **중분류**: 제작/배송
- **처리 내용(기술)**: 해당 funding의 `shipments`가 없으면 생성(`PREPARING`), carrier·tracking_number 저장 후 `status='SHIPPED'`, `shipped_at=now()`로 전이
- **출력값**: 등록된 발송정보(상태 포함)
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: 요구사항정의서 8.3.3 "API 연결 없이 목업 구현만 진행"에 따라 실제 택배사 배송추적 연동 없이 값만 저장·노출한다. 실시간 배송위치 조회 등은 이번 범위 밖[가정]. **(2차 검토)** 초안에 있던 "프로젝트의 모든 funding이 SHIPPED 이상이 되면 DELIVERY로 자동 전이" 로직은 제거함 — fulfillment-service는 프로젝트의 전체 funding 개수를 알 수 없어 "모두 발송됨"을 판정할 방법이 없었다. DELIVERY 단계 전이는 FULFILLMENT-002(판매자 수동 조작)로만 이루어진다.
---
 
## 7. FULFILLMENT-007 — 배송완료 목업 처리 `신규`
 
- **PRD 코드**: -
- **권한**: 시스템
- **담당 서비스**: fulfillment-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 배치 쿼리 바인딩 변수 사용
- **소분류**: 배송완료 목업 처리
- **예외 처리**: -
- **요구사항**: 택배사 실연동 없이도 발송 후 일정 기간이 지나면 "배송완료" 상태로 전환해 수령확인 플로우를 시작할 수 있게 한다
- **우선순위**: MVP
- **입력값**: (배치 트리거, 파라미터 없음)
- **중분류**: 제작/배송
- **처리 내용(기술)**: `shipments.status='SHIPPED'`이면서 `shipped_at`이 3영업일[정책값, 확정 필요] 이상 경과한 건을 조회해 `status='DELIVERED'`, `delivered_at=now()`로 전이. 실제 택배사 API 연동 시 이 배치는 웹훅 수신 로직으로 교체될 자리[가정]
- **출력값**: 처리된 건수
- **트리거 방식**: 스케줄러
- **검토의견(변경사항)**: 신규 — 요구사항정의서 8.3.3(목업 구현) 전제 하에서 "배송완료"(14.1.3 상태값, 14.3.4 "배송 완료 후 수령확인") 상태 자체를 만들어낼 방법이 원본 기능명세서에 없었음. `FulfillmentERD.md` 검토의견 2 참고
---
 
## 8. FULFILLMENT-008 — 배송 상태 내부 조회 API (payment-service 연동) `신규/이관`
 
- **PRD 코드**: -
- **권한**: 시스템
- **담당 서비스**: fulfillment-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 내부 전용 엔드포인트(게이트웨이 우회 차단, `InternalGatewaySecretFilter`와 동일한 내부 API 키 검증 적용)
- **소분류**: 배송 상태 내부 조회
- **예외 처리**: order-service 내부 API(`GET /internal/fundings/{fundingId}`) 호출 실패/타임아웃 → 503(`DEPENDENCY_FAILURE`, payment-service는 재시도 또는 판정 보류) / 존재하지 않는 fundingId → 404
- **요구사항**: payment-service가 발송지연 결제취소(PAYMENT-008)·하자환불 신청기간(PAYMENT-006) 판정에 필요한 배송 상태를 조회한다
- **우선순위**: MVP
- **입력값**: fundingId
- **중분류**: 제작/배송
- **처리 내용(기술)**: 해당 funding의 `shipments` 레코드가 있으면 그 값(`status`·`shipped_at`·`delivered_at`·`receipt_confirmed_at`)을 그대로 사용해 `isAlreadyShipped`(`status IN ('SHIPPED','DELIVERED','RECEIPT_CONFIRMED')`)를 계산. 레코드가 없으면(아직 발송 전) order-service의 기존 내부 API `GET /internal/fundings/{fundingId}`(payment-service `OrderFundingClient`가 이미 호출 중인 것과 동일 엔드포인트)를 호출해 `projectId`를 조회한 뒤, 해당 프로젝트의 `SHIPPING_OUT` 단계 최신 `fulfillment_stage_details.planned_end_at`(발송 예정일)과 현재 시각을 비교해 `isDelayed`를 계산
- **출력값**: `{ isAlreadyShipped, isDelayed, deliveredAt, receiptConfirmedAt }`
- **트리거 방식**: API 호출(내부, 동기)
- **검토의견(변경사항)**: 이관/신규 — payment-service `ShippingStatusClient`(FS-096 대응, `StubShippingStatusClient`가 항상 "미발송"으로 응답 중)와 order-service `OrderDomainApiSpec.md`의 "배송 진행 단계는 shipping-service 조회 필요"라는 가정을 실제로 구현하는 항목. 또한 payment-service PAYMENT-006(하자환불, "수령 후 기간 내" 신청기간)이 참조할 `receiptConfirmedAt`도 이 API가 유일한 출처임 — 지금까지는 이 값 자체가 어느 서비스에도 없었다. **(2차 검토)** 초안은 미발송 건을 그냥 "미발송으로 응답"하고 끝내려 했으나, 발송 전에는 `shipments`(project_id를 들고 있는 유일한 로컬 테이블)에 행 자체가 없어 `isDelayed` 계산에 필요한 `projectId`를 구할 수 없었다 — 정작 PAYMENT-008이 가장 궁금해하는 케이스(발송 안 됨+지연 여부)를 판정 못 하는 설계였다. order-service 내부 API로 `projectId`를 조회하도록 수정.
---
 
## 9. FULFILLMENT-009 — 수령 확인 처리 (구매자)
 
- **PRD 코드**: FL_B_MY_02_01
- **권한**: 구매자
- **담당 서비스**: fulfillment-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4] 본인 funding에 대해서만 수령확인 가능 — funding 소유자(memberId)는 order-service 내부 API 조회 또는 게이트웨이가 전달한 계정과 대조[가정]
- **소분류**: 수령 확인 처리
- **예외 처리**: 이미 `RECEIPT_CONFIRMED` 상태인 건에 재요청 → idempotent 200(에러 아님) / `PREPARING`·`SHIPPED` 상태(아직 배송완료 전)에서 시도 시 422
- **요구사항**: 배송 완료 후 구매자가 리워드 수령을 확인 처리한다
- **우선순위**: MVP
- **입력값**: projectId, fundingId
- **중분류**: 제작/배송
- **처리 내용(기술)**: `shipments.status='DELIVERED'`인 건을 `status='RECEIPT_CONFIRMED'`, `receipt_confirmed_at=now()`로 전이(`receipt_auto_confirmed=false`). 수령확인 시점이 payment-service PAYMENT-006(하자환불) 신청기간의 기산일이 됨
- **출력값**: 수령확인 결과
- **트리거 방식**: API 호출
---
 
## 10. FULFILLMENT-010 — 미확인 배송 자동 확정 처리 `신규`
 
- **PRD 코드**: FL_B_MY_02_01(파생)
- **권한**: 시스템
- **담당 서비스**: fulfillment-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 배치 쿼리 바인딩 변수 사용
- **소분류**: 자동 확정 처리
- **예외 처리**: -
- **요구사항**: 배송완료 후 일정 기간 구매자가 수령확인을 하지 않으면 시스템이 자동으로 수령을 확정 처리한다
- **우선순위**: MVP
- **입력값**: (배치 트리거, 파라미터 없음)
- **중분류**: 제작/배송
- **처리 내용(기술)**: `status='DELIVERED'`이면서 `delivered_at`이 7일[정책값, 확정 필요] 이상 경과한 건을 조회해 `status='RECEIPT_CONFIRMED'`, `receipt_confirmed_at=now()`, `receipt_auto_confirmed=true`로 전이. 자동 확정 시에도 구매자에게 안내 알림 발행
- **출력값**: 자동 확정 처리된 건수
- **트리거 방식**: 스케줄러
- **검토의견(변경사항)**: 신규 — 요구사항정의서 14.3.4 "미확인 시 자동 확정 처리"에 대응하는 배치가 원본 기능명세서에 없었음
 
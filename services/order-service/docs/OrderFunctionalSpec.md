# order-service 기능명세서 (ORDER-001~016)

> 요구사항정의서 v0.5, 기능명세서_전체.xlsx(ORDER-001~010)를 기준으로 하되, `OrderPayment_ERD_검토및수정.md` / `OrderPayment_기능명세서_검토및수정.md`에서 정리한 수정·추가 사항을 반영해 재작성했습니다.
> 기존 ID 대비 변경: ORDER-002/003 처리내용 수정, ORDER-011~015 신규 추가(ORDER-015는 **기능명세서_전체.xlsx 원본의 PAYMENT-012**(쿠폰 사용처리/복원)를 소유권 정정 후 이관 — `PaymentDomainFunctionalSpec.md`의 현재 PAYMENT-012(쿠폰 정산 차감, 원본에서는 PAYMENT-013이었음)와는 **다른 항목**이니 혼동 주의), ORDER-016은 `OrderApiSpec.md` 작성 중 GitHub의 project-service 실제 코드(`RewardEventPublisher`)를 확인하고 뒤늦게 추가.

---

## 1. ORDER-001 — 서포터 활동 목록 조회

- **PRD 코드**: FL_B_PJ_01_05
- **권한**: 공통
- **담당 서비스**: order-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S9] 공개설정 미동의 참여자는 표시명·금액을 마스킹(익명 처리)해 개인정보 노출 방지
- **소분류**: 서포터 활동 목록 조회
- **예외 처리**: 참여자 없음 → Empty State / 로드 실패 → 안내+재시도
- **요구사항**: 프로젝트에 참여한 서포터들의 활동을 시간순으로 노출한다
- **우선순위**: P1
- **입력값**: projectId,페이지네이션
- **중분류**: 프로젝트 상세
- **처리 내용(기술)**: 펀딩 참여 내역을 최신순 정렬, 공개설정은 member-service 동기 조회. 비공개는 `displayName="익명"`·`amount=null`, 공개 동의여도 닉네임 대신 `"구매자"`로 일반화, 무한스크롤
- **출력값**: 서포터 활동 목록(표시명,금액(선택),상대시간)
- **트리거 방식**: API 호출

---

## 2. ORDER-002 — 결제금액 계산/주문서 생성

- **PRD 코드**: FL_B_PY_01_02
- **권한**: 구매자
- **담당 서비스**: order-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4] 본인 계정 기준으로만 계산 / 금액 계산은 서버에서만 수행, 클라이언트가 보낸 금액 신뢰 금지
- **소분류**: 결제금액 계산(미리보기)
- **예외 처리**: -
- **요구사항**: 선택 리워드·배송지·쿠폰을 기준으로 최종 결제 금액을 확인한다(DB 비영속)
- **우선순위**: MVP
- **입력값**: projectId, lineItems(rewardId/quantity/optionValueIds), shippingAddress(요청 바디 필수이나 계산 미사용), couponCodes(선택, 최대 2개 — 플랫폼쿠폰 1개+메이커쿠폰 1개까지)
- **중분류**: 펀딩 참여·결제
- **처리 내용(기술)**: DB에 아무것도 쓰지 않는 비영속 미리보기. 리워드금액 합산 + 배송비 고정 3,000원(`order.policy.default-shipping-fee`) + 쿠폰 할인 반영(FREE_SHIPPING 쿠폰은 배송비 한도 내 할인) → 최종 금액만 계산해 반환. 임시 주문서를 생성하지 않는다
- **출력값**: 최종 결제 금액(리워드금액+배송비-쿠폰할인), 배송비, 적용/불가 쿠폰 목록
- **트리거 방식**: API 호출 (`POST /api/v1/orders/preview`)
- **검토의견(변경사항)**: 수정 — 기존 "임시 주문서 생성"은 구현과 불일치. ORDER-010과 동일 엔드포인트이며 입력은 `fundingId`가 아니라 `projectId`+`lineItems`. 배송비는 프로젝트/리워드 정책값이 없어 3,000원 고정

---

## 3. ORDER-003 — 펀딩 주문 생성(재고 검증/차감)

- **PRD 코드**: FL_B_PY_01_03
- **권한**: 구매자
- **담당 서비스**: order-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S1·S4] 서버에서 가격·재고를 재검증 후 처리, 클라이언트가 보낸 금액 신뢰 금지(S4) / 재고 차감 쿼리는 바인딩 변수 사용(S1) / 본인 명의로만 생성(S4)
- **소분류**: 펀딩 주문 생성(재고 검증/차감)
- **예외 처리**: 재고 부족 → 409 CONFLICT, 트랜잭션 롤백(재고 변경 없음)
- **요구사항**: 리워드 선택 → 결제 요청 트리거로 이어지는 주문을 생성한다
- **우선순위**: MVP
- **입력값**: 선택 리워드/옵션/수량,주문서 정보
- **중분류**: 펀딩 참여·결제
- **처리 내용(기술)**: 하나의 트랜잭션으로 order-service 소유 `inventories.available_stock`을 대상으로 재고 검증 및 조건부 UPDATE(낙관적 락, `version` 컬럼)로 직접 차감(`reserved_stock`은 항상 0, 미사용) → `Funding`/`FundingLineItem` 생성(status=PENDING, `payment_expires_at`=생성시각+30분, 가격·프로젝트명 스냅샷 저장)
- **출력값**: 생성된 주문(orderId=public_id UUID, status=PENDING, paymentExpiresAt)
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: 수정 — 기존 "project-service에 재고 차감 위임"은 ERD 정책("재고 원장·차감 로직은 order-service가 전담")과 모순되어 정정. 만료 유예는 `order.policy.payment-expiry-minutes=30`. `reserved_stock` 컬럼은 있으나 차감/원복에 쓰지 않음

---

## 4. ORDER-004 — 내 펀딩 참여 목록 조회

- **PRD 코드**: FL_B_MY_01_01
- **권한**: 구매자
- **담당 서비스**: order-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4] 본인 참여 내역만 조회 가능
- **소분류**: 내 펀딩 참여 목록 조회
- **예외 처리**: 로드 실패 → 안내+재시도
- **요구사항**: 본인이 참여한 프로젝트 목록과 상태를 확인한다
- **우선순위**: MVP
- **입력값**: 페이지네이션,(선택)상태필터
- **중분류**: 마이페이지
- **처리 내용(기술)**: `member_id` 기준 본인 참여 내역 조회. `finalAmount`는 **쿠폰을 적용하지 않고** `totalRewardAmount + shippingFee`만 합산한다(상세 조회와 다름). `projectTitle`은 생성 시점 스냅샷
- **출력값**: 참여 프로젝트 목록(orderId UUID, 상태, 쿠폰 미적용 finalAmount, projectTitle 스냅샷)
- **트리거 방식**: API 호출

---

## 5. ORDER-005 — 개별 펀딩 참여 상세 조회

- **PRD 코드**: FL_B_MY_01_02
- **권한**: 구매자
- **담당 서비스**: order-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4] orderId 조작으로 타인 주문 접근 불가, 소유권 서버 검증
- **소분류**: 개별 펀딩 참여 상세 조회
- **예외 처리**: 불가한 액션 시도 → 사유 안내(예: 모금종료 후 취소)
- **요구사항**: 선택한 프로젝트의 참여 정보와 가능한 액션을 확인한다
- **우선순위**: MVP
- **입력값**: orderId
- **중분류**: 마이페이지
- **처리 내용(기술)**: 참여 정보(리워드,옵션,금액,상태) 및 상태별 가능 액션 반환. `finalAmount`는 **쿠폰을 적용**한다(`totalRewardAmount + shippingFee - discountAmount`). `paidAt`은 payment-service 소관이라 응답에서 생략
- **출력값**: 참여 상세 정보(discountAmount 포함), 가능 액션 목록
- **트리거 방식**: API 호출

---

## 6. ORDER-006 — 펀딩 마감 목표달성 판정

- **PRD 코드**: FL_B_RF_01_02
- **권한**: 시스템
- **담당 서비스**: order-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S1] 바인딩 변수 사용
- **소분류**: 펀딩 마감 목표달성 판정
- **예외 처리**: -
- **요구사항**: 펀딩 마감 시점에 목표금액 달성 여부를 판정한다
- **우선순위**: MVP
- **입력값**: `project.funding-deadline-reached.v1` (`projectId`, `goalAmount`) — order-service가 마감 시각을 스스로 스케줄하지 않음
- **중분류**: 환불
- **처리 내용(기술)**: project-service 마감 이벤트를 구독 → 해당 프로젝트의 활성 펀딩 누적금액과 `goalAmount` 비교 → **펀딩 건마다** 상태 전이 후 `funding.succeeded.v1`(payload: `eventId`, `fundingId`(Long PK), `projectId`, `sellerId`, `achievedAt`) 또는 `funding.goal-failed.v1`(`eventId`, `fundingId`, `projectId`) 발행. REST의 `orderId`(UUID)와 이벤트의 `fundingId`(Long)는 다른 식별자
- **출력값**: 성립/미성립 판정 결과(펀딩 건별 이벤트)
- **트리거 방식**: 이벤트 구독(`project.funding-deadline-reached.v1`)
- **검토의견(변경사항)**: 리스너는 구현됨. **project-service는 이 토픽을 아직 발행하지 않아** 성립/미달 판정이 실제로 돌지 않는다.

---

## 7. ORDER-007 — 쿠폰 발급(플랫폼 자동)

- **PRD 코드**: FL_C_CP_01
- **권한**: 시스템
- **담당 서비스**: order-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1·S4] 본인 대상 쿠폰만 발급
- **소분류**: 쿠폰 발급(플랫폼 자동)
- **예외 처리**: 발급 수량/예산 한도 초과 시 발급 중단
- **요구사항**: 신규가입 시 플랫폼이 웰컴 쿠폰 1종을 자동으로 발급한다
- **우선순위**: P1
- **입력값**: `member.signed-up.v1` (`memberId`)
- **중분류**: 쿠폰
- **처리 내용(기술)**: `order.policy.welcome-coupon-code`가 설정된 경우에만 해당 쿠폰 1종을 `autoIssue`. 코드가 비어 있으면 발급하지 않음. 등급산정·이벤트 조건 범위는 구현하지 않음. 발급주체=플랫폼이므로 할인분은 플랫폼 부담(정산 미차감)
- **출력값**: 발급된 쿠폰(또는 미설정/소진 시 no-op)
- **트리거 방식**: 이벤트 구독(`member.signed-up.v1`)

---

## 8. ORDER-008 — 쿠폰 발급(메이커)

- **PRD 코드**: FL_C_CP_01
- **권한**: 판매자
- **담당 서비스**: order-service
- **대분류**: 판매자
- **보안/권한 고려사항**: [S1·S2·S4] 본인 소유 프로젝트에만 쿠폰 발급 가능
- **소분류**: 쿠폰 발급(메이커)
- **예외 처리**: 발급 예산 한도(budget_limit) 초과 시 발급 차단
- **요구사항**: 메이커가 자신의 프로젝트 전용 쿠폰을 생성한다
- **우선순위**: P1
- **입력값**: projectId,할인방식,할인값,수량,예산한도,유효기간
- **중분류**: 쿠폰
- **처리 내용(기술)**: 할인방식·금액·수량·예산한도(`budget_limit`)·기간 설정. 발급주체=메이커이므로 할인분은 해당 메이커 정산에서 차감(`PaymentDomainFunctionalSpec.md`의 PAYMENT-012 "쿠폰 정산 차감"과 연계)
- **출력값**: 생성된 쿠폰
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: 수정 — 예산 한도(budget_limit) 필드가 ERD에 없었던 것을 추가함에 따라 처리내용·예외처리에 명시

---

## 9. ORDER-009 — 쿠폰함 조회

- **PRD 코드**: FL_B_MY_CP
- **권한**: 구매자
- **담당 서비스**: order-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4] 본인 보유 쿠폰만 조회 가능
- **소분류**: 쿠폰함 조회
- **예외 처리**: 보유 쿠폰 없을 시 안내 문구
- **요구사항**: 회원이 보유 쿠폰과 상태(사용가능/사용완료/만료)를 조회한다
- **우선순위**: P1
- **입력값**: (로그인 회원 기준), (선택)status, 페이지네이션
- **중분류**: 쿠폰
- **처리 내용(기술)**: 본인 보유 쿠폰을 `PageResponse`로 반환. 유효기간 임박(기본 3일) 쿠폰은 order-service 배치가 `notification.raised.v1`(`notifType=COUPON_EXPIRING`)을 발행한다 — notification-service 배치가 쿠폰함을 직접 읽지 않음
- **출력값**: `PageResponse`(content + page/size/totalElements/totalPages/hasNext). 각 항목: 상태,할인방식,유효기간
- **트리거 방식**: API 호출

---

## 10. ORDER-010 — 쿠폰 적용/해제

- **PRD 코드**: FL_B_PY_CP
- **권한**: 구매자
- **담당 서비스**: order-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S1·S4] 본인 보유 쿠폰만 적용 가능, 서버에서 할인 재계산(클라이언트 값 미신뢰)
- **소분류**: 쿠폰 적용/해제
- **예외 처리**: 최소금액 미달 → 자동 해제+안내 / 만료 쿠폰 적용 시도 → 사용불가 안내
- **요구사항**: 주문서 작성 시 보유 쿠폰을 적용해 결제 금액을 할인받는다
- **우선순위**: P1
- **입력값**: ORDER-002와 동일 — `POST /api/v1/orders/preview`의 `projectId`, `lineItems`, `couponCodes`(최대 2개 — 플랫폼쿠폰 1개+메이커쿠폰 1개까지, 동일 issuer_type 중복 불가). `fundingId`를 받지 않음
- **중분류**: 쿠폰
- **처리 내용(기술)**: 비영속 미리보기에서 `couponCodes`를 넣어 재계산. 적용 불가 쿠폰은 `unavailableCoupons` 사유 코드로 안내(preview는 422를 내지 않음). 최대할인 자동추천은 미구현. 중복 사용은 플랫폼쿠폰 1개+메이커쿠폰 1개까지(동일주체 중복불가)
- **출력값**: 재계산된 최종 결제금액, appliedCoupons, unavailableCoupons
- **트리거 방식**: API 호출 (ORDER-002와 동일 엔드포인트)

---

## 11. ORDER-011 — 재입고(품절) 알림 신청 `신규`

- **PRD 코드**: FL_B_PY_01_01
- **권한**: 구매자
- **담당 서비스**: order-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4] 본인 명의로만 신청 가능
- **소분류**: 재입고(품절) 알림 신청
- **예외 처리**: 이미 신청한 리워드 재신청 시 → 중복 신청 없이 기존 신청 유지 안내
- **요구사항**: 품절된 리워드에 대해 재입고(추가 오픈) 시 알림을 받도록 신청한다
- **우선순위**: P1
- **입력값**: rewardId
- **중분류**: 펀딩 참여·결제
- **처리 내용(기술)**: `reward_restock_notify_requests`에 (reward_id, member_id) upsert. 이후 ORDER-016이 품절→재입고를 감지하면 신청자마다 `notification.raised.v1`(`notifType=REWARD_RESTOCK`)을 발행하고 신청 레코드를 삭제한다
- **출력값**: 신청 결과
- **트리거 방식**: API 호출(신청) + 이벤트(재입고 알림 발행)
- **검토의견(변경사항)**: 신규 — 요구사항정의서 13.1.4 "품절 리워드 선택 시 [알림 신청] 버튼 대체"에 대응하는 항목이 없었으나 ERD에는 `reward_restock_notify_requests` 테이블이 이미 존재했음. 알림 발송은 notification-service 배치가 아니라 order-service가 `notification.raised.v1`을 발행

---

## 12. ORDER-012 — 쿠폰 발급받기(소비자 능동 클레임) `신규`

- **PRD 코드**: FL_B_LV_CP
- **권한**: 구매자
- **담당 서비스**: order-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S1·S4] 동시 요청 다수 발생 시 낙관적 락 재시도 또는 실패 처리(초과 발급 방지), 본인 명의로만 발급
- **소분류**: 쿠폰 발급받기(선착순/드롭)
- **예외 처리**: 재고(remaining_quantity) 소진 → 발급 불가 안내 / 1인 발급 한도(per_member_limit) 초과 → 중복 발급 불가 안내 / 미시청·LIVE 종료 후 시도 → 발급 불가 안내
- **요구사항**: 소비자가 LIVE 방송 중 노출된 쿠폰을 직접 "받기"하여 발급받는다(선착순 수량·잔여 표시)
- **우선순위**: P1
- **입력값**: couponCode
- **중분류**: 쿠폰
- **처리 내용(기술)**: `coupons.remaining_quantity`를 `version` 낙관적 락으로 조건부 차감 후 `coupon_issuances` 생성. GENERAL/LIVE 구분 없이 동일 엔드포인트. LIVE "방송 중" 검증은 live-service 연동 전이라 생략. 소진 시 즉시 마감
- **출력값**: 발급된 쿠폰(coupon_issuance)
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: 신규 — 요구사항정의서 16.6.4 "LIVE 방송 화면에서 쿠폰 받기"는 ORDER-007(시스템 자동 발급)과 달리 소비자 능동 클레임이나 대응 항목이 없었음

---

## 13. ORDER-013 — 미결제 주문 자동 만료 처리 `신규`

- **PRD 코드**: FL_B_PY_01_03 (13.3.3 파생)
- **권한**: 시스템
- **담당 서비스**: order-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S1] 배치 쿼리 바인딩 변수 사용
- **소분류**: 미결제 주문 자동 만료
- **예외 처리**: 배치 실행 중 재고 원복 실패 → 로그 기록 후 재시도 대상으로 표시
- **요구사항**: 결제를 완료하지 않고 방치된 주문(PENDING)의 재고 점유를 해제한다
- **우선순위**: MVP
- **입력값**: (배치 트리거, 파라미터 없음)
- **중분류**: 펀딩 참여·결제
- **처리 내용(기술)**: `payment_expires_at`이 경과한 status=PENDING 건을 조회 → status=PAYMENT_EXPIRED로 전이 → 차감했던 `available_stock` 원복(`reserved_stock`은 사용하지 않음)
- **출력값**: 만료 처리된 주문 건수
- **트리거 방식**: 스케줄러(기본 60초)
- **검토의견(변경사항)**: 신규 — `inventories.reserved_stock` 컬럼은 있으나 예약 차감 경로는 구현하지 않음. 만료 유예시간은 `order.policy.payment-expiry-minutes=30`

---

## 14. ORDER-014 — 참여 취소(단순변심) 가능 판정 및 취소 이벤트 발행 `신규`

- **PRD 코드**: FL_B_RF_01_01
- **권한**: 구매자
- **담당 서비스**: order-service
- **대분류**: 소비자
- **보안/권한 고려사항**: [S4] 본인 주문만 취소 가능, orderId 조작 차단
- **소분류**: 참여 취소(단순변심) 가능 판정
- **예외 처리**: 펀딩 마감 이후 시도 → 취소 불가 안내+환불정책 안내
- **요구사항**: 펀딩 마감 전, 참여자가 단순변심으로 펀딩 참여를 철회한다
- **우선순위**: MVP
- **입력값**: orderId
- **중분류**: 환불
- **처리 내용(기술)**: 펀딩 진행중(마감 전) 상태 검증 → `fundings.status=CANCELLED_BY_MEMBER` 전이 → `available_stock` 원복 → `funding.cancelled-by-member.v1` 발행(payload: `eventId`, `fundingId`(Long), `projectId`, `memberId`. 결제취소·환불 실행은 PAYMENT-004가 이벤트 구독으로 처리)
- **출력값**: 취소 접수 결과
- **트리거 방식**: API 호출
- **검토의견(변경사항)**: 신규(기존 PAYMENT-004에서 분리) — 기존 PAYMENT-004는 담당 서비스가 "order-service, payment-service"로 이원 표기되어 있었음. 마감 여부 판정·상태 전이는 order-service 소관, 실제 결제취소·환불 실행은 payment-service 소관으로 역할을 분리하고 ORDER-006 → PAYMENT-005 패턴과 동일하게 이벤트로 연결

---

## 15. ORDER-015 — 쿠폰 사용처리·복원 `이관`(기능명세서_전체.xlsx 원본의 PAYMENT-012 — `PaymentDomainFunctionalSpec.md`의 현재 PAYMENT-012 "쿠폰 정산 차감"과는 다른 항목)

- **PRD 코드**: FL_C_CP_02
- **권한**: 시스템
- **담당 서비스**: order-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 이벤트 소스 검증
- **소분류**: 쿠폰 사용처리/복원
- **예외 처리**: 복원 시점에 이미 만료된 쿠폰 → 복원 불가 안내
- **요구사항**: 결제 완료 시 쿠폰을 사용완료 처리하고, 환불 발생 시 유형별 규칙에 따라 복원한다
- **우선순위**: P1
- **입력값**: `PaymentCompleted`/`RefundCompleted` 이벤트(fundingId Long, couponIssuanceId, 환불유형, 전액환불여부) — **현재는 소비되지 않음**
- **중분류**: 쿠폰
- **처리 내용(기술)**: `PaymentEventSyncService`에 로직은 구현됨 — 결제성공→쿠폰 사용완료 + `PENDING`→`FUNDING_IN_PROGRESS` / 미달자동환불→쿠폰 복원 / 마감전 단순변심취소→미복원 / 성립후 하자·지연환불→전액환불 건만 복원(+ `REFUNDED_AFTER_SUCCESS`). 이미 `EXPIRED`면 복원하지 않음. **그러나 `@KafkaListener`가 없어 `payment.completed.v1`/`refund.completed.v1`을 구독하지 않는다(미배선)**
- **출력값**: 처리 결과(배선 후)
- **트리거 방식**: 이벤트 구독 예정(`payment.completed.v1`/`refund.completed.v1`) — **현재 미구현 배선**
- **검토의견(변경사항)**: 이관 — 기능명세서_전체.xlsx 원본의 PAYMENT-012(쿠폰 사용처리/복원)는 담당 서비스가 payment-service였으나, 실제로 갱신하는 테이블(`coupons`,`coupon_issuances`,`funding_coupon_applications`)이 전부 order-service DB에 있어 DB-per-service 원칙과 충돌. 이미 확립된 "쿠폰 상태 업데이트는 order-service 소유" 컨벤션에 맞춰 이관. (참고: 원본에서 정산 차감을 다루던 PAYMENT-013은 번호가 당겨져 지금의 PAYMENT-012 "쿠폰 정산 차감"이 됨 — 이름은 비슷해 보여도 서로 다른 기능). **구현 현황**: 애플리케이션 서비스는 있으나 Kafka 리스너가 없어 PaymentCompleted를 소비한다고 문서화하면 안 됨

---

## 16. ORDER-016 — 리워드 생성/수정 이벤트 구독(재고 원장 동기화) `신규`

- **PRD 코드**: -
- **권한**: 시스템
- **담당 서비스**: order-service
- **대분류**: 공통
- **보안/권한 고려사항**: [S1] 이벤트 소스 검증(project-service가 발행한 이벤트인지 확인)
- **소분류**: 리워드 생성/수정 이벤트 구독(재고 원장 동기화)
- **예외 처리**: 이미 존재하는 reward_id의 `RewardCreated` 재수신(재시도) → upsert로 멱등 처리 / `isLimited=false`로 변경된 경우 → 해당 `inventories` 행 삭제 / `RewardUpdated`로 수량이 줄어들어 `기존 available_stock - 감소분 < 0`이 되는 경우 → `available_stock=0`으로 클램프하고 초과분은 로그로 남겨 운영 확인 대상으로 표시(품절 처리는 되지만 이미 판매된 수량보다 적게 재설정되지 않도록 보호)
- **요구사항**: project-service에서 리워드가 생성·수정될 때 order-service의 재고 원장(inventories)을 동기화한다
- **우선순위**: MVP
- **입력값**: `reward.created.v1`/`reward.updated.v1` (`rewardId`, `projectId`, `isLimited`, `quantity`)
- **중분류**: 재고
- **처리 내용(기술)**: **구현됨.** `@KafkaListener`가 두 토픽을 구독. `RewardCreated`는 `isLimited=true`인 경우에만 `inventories(reward_id, available_stock=quantity, reserved_stock=0, initial_quantity=quantity, version=0)` 신규 생성(`isLimited=false`면 행을 두지 않음). `RewardUpdated`는 **절대값으로 덮어쓰지 않고 `initial_quantity` 대비 증분(delta)만 반영** — `delta = newQuantity - inventories.initial_quantity`, `available_stock += delta`(0 미만이면 0으로 클램프) 후 `initial_quantity`를 새 값으로 갱신. `isLimited=false`로 변경된 경우 해당 `inventories` 행 삭제(무제한 전환). 품절(`available_stock<=0`)에서 재입고되면 ORDER-011 신청자에게 `notification.raised.v1` 발행
- **출력값**: 동기화 처리 결과
- **트리거 방식**: 이벤트 구독(`reward.created.v1`/`reward.updated.v1`, project-service가 아웃박스로 발행)
- **검토의견(변경사항)**: 구현 완료 — `initial_quantity` 컬럼으로 델타를 계산한다. `previousQuantity`를 이벤트에 실을 필요는 없음. `reserved_stock`은 동기화·차감 모두에서 0으로 유지.

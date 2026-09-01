# Project 기능 명세서

project-service(project 도메인)의 상세 기능 명세입니다. 서비스 간 흐름·책임 경계는 루트 `docs/PRD.md`, 공통 규칙(에러코드/보안 등)은 `.claude/rules/`를 참고하세요. 출처: `ProjectDomainApiSpec.md`, `ERD.md`(catalog-service 섹션 — ERD 문서상의 원래 명칭)

## 범위

이 문서는 project-service가 담당하는 **프로젝트 생성·관리, 검수, 리워드/옵션, 새소식, 커뮤니티, 서포터 후기, 오픈알림·팔로우**를 다룹니다. 재고 수량·펀딩 참여·결제·쿠폰·환불은 order-service, 회원 프로필·사업자정보는 member-service 담당이라 이 문서에 포함하지 않습니다(PRD.md 2. 도메인/서비스 개요 기준).

---

## 프로젝트 관리

### PROJECT-001. 신규 프로젝트 생성

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_S_PR_02_01 |
| 권한 | 판매자 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 판매자가 신규 펀딩 프로젝트 등록을 시작한다.
- **처리 내용**: 로그인 사용자를 `seller_id`로 하는 `projects` 행을 `status=DRAFT`로 생성. `public_id`는 서버에서 UUID v7로 생성해 이후 모든 프로젝트 API의 경로 파라미터로 사용.
- **입력값**: 없음(`CurrentUser`를 `seller_id`로 사용)
- **출력값**: `projectId`(public_id), `status`
- **예외 처리**: 생성 실패 시 오류 안내 후 재시도 가능해야 함
- **보안/권한**: 로그인 필수(비로그인 401) · `seller_id`는 클라이언트 입력값이 아니라 서버가 `CurrentUser`로 채움(위조 방지)

### PROJECT-002. 프로젝트 목록 조회 (판매자)

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_S_PR_01_01 |
| 권한 | 판매자 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 판매자가 본인이 생성한 프로젝트 목록과 상태를 확인한다.
- **처리 내용**: `seller_id = CurrentUser.id` 조건으로만 조회. 상태값(전체/준비 중/진행 중/종료) 탭 필터 제공 — "준비 중"은 `DRAFT`·`PENDING_REVIEW`, "종료"는 `SUCCEEDED`·`FAILED`를 묶어 반환. `dDay`는 `funding_deadline` 기준 서버 계산.
- **입력값**: `status`(선택), `page`(기본 0), `size`(기본 20)
- **출력값**: 프로젝트 목록(제목/썸네일/상태/생성일/마감일/dDay), 페이지 정보
- **예외 처리**: 목록 없으면 `content: []`, 클라이언트가 Empty State 처리
- **보안/권한**: 타 판매자 프로젝트 조회 불가(쿼리 자체에 `seller_id` 조건 강제)

### PROJECT-003. 프로젝트 상세 조회

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_B_PJ_01_01 |
| 권한 | 비로그인(단, `DRAFT`/`PENDING_REVIEW`는 판매자 본인만) |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 사용자가 프로젝트 소개·목표금액·리워드·펀딩현황 등 정보를 확인한다.
- **처리 내용**: `status NOT IN ('DRAFT','PENDING_REVIEW')`면 누구나 조회 가능, 그 외엔 `seller_id = CurrentUser.id` 아니면 404. `currentAmount`/`achievementRate`/`participantCount`는 order-service 집계값을 5분 캐시로 병합.
- **입력값**: `projectId`(path)
- **출력값**: 프로젝트 상세 정보(소개/카테고리/목표금액/펀딩현황/일정/상태/환불정책 요약)
- **예외 처리**: 정보 로드 실패 시 해당 영역에 안내 문구·재시도 버튼
- **보안/권한**: 비공개 프로젝트(작성/검수 중) 접근 시 소유자 확인, 아니면 404(403이 아닌 404로 존재 여부 자체를 비노출)

### PROJECT-004. 프로젝트 기본정보 등록/수정

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_S_PR_02_02 |
| 권한 | 판매자 본인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 프로젝트 생성에 필요한 사업자 유형·카테고리·목표금액 등 기본정보를 등록한다.
- **처리 내용**: `(category_major, category_minor)` 조합이 `categories` 마스터 테이블에 존재하는지 검증 후 저장. `goalAmount >= 500000` 검증. `privacyAgreed=false`면 저장 거부.
- **입력값**: `businessType`, `categoryMajor`, `categoryMinor`, `goalAmount`, `privacyAgreed`
- **출력값**: `projectId`, `status`
- **예외 처리**: `goalAmount < 500000` → 400 / `privacyAgreed=false` → 저장 거부, 동의 모달 재노출 / 카테고리 조합이 마스터에 없음 → 400 `INVALID_CATEGORY`
- **보안/권한**: `seller_id != CurrentUser.id` → 403 · `status=PENDING_REVIEW`인 프로젝트는 수정 불가(423 `RESOURCE_LOCKED`)

### PROJECT-005. 프로젝트 상세페이지 등록/임시저장

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_S_PR_03_01 |
| 권한 | 판매자 본인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 판매자가 상세페이지(제목·대표이미지·소개)를 작성하거나 임시저장하고, 완료 시 검수를 요청한다.
- **처리 내용**: `isDraft=true`면 필수값 미충족 상태로도 저장 허용. `isDraft=false`(검수 요청)면 필수 항목(제목/대표이미지/소개/리워드 1개 이상) 검증 후 통과 시 `status`를 `PENDING_REVIEW`로 전환하고 `project_review_requests`에 `SUBMITTED` 행 생성.
- **입력값**: `title`(40자 이내), `thumbnailImageUrl`, `introContent`, `isDraft`
- **출력값**: `projectId`, `savedAt`
- **예외 처리**: `title` 40자 초과 → 400 / `isDraft=false`인데 필수 항목 누락 → 400과 누락 필드 목록 / 저장 실패 시 재시도 가능
- **보안/권한**: `seller_id != CurrentUser.id` → 403 · `status=PENDING_REVIEW`인 프로젝트는 `isDraft=true`(단순 임시저장) 호출 시 423 `RESOURCE_LOCKED`, `isDraft=false`(검수 재요청) 호출 시 409 `CONFLICT`로 구분해 막는다

### PROJECT-006. 프로젝트 삭제

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_S_PR_01_01 |
| 권한 | 판매자 본인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 판매자가 작성 중인 프로젝트를 삭제한다.
- **처리 내용**: `deleted_at`을 채우는 소프트 삭제.
- **입력값**: `projectId`(path)
- **출력값**: 처리 결과 메시지
- **예외 처리**: `status IN ('PENDING_REVIEW','ONGOING')`인 프로젝트는 삭제 불가 → 422 `PROJECT_NOT_DELETABLE` / 클라이언트에서 삭제 전 확인 팝업 노출
- **보안/권한**: `seller_id != CurrentUser.id` → 403

### PROJECT-007. 프로젝트 검수 승인/반려

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | PRD 미정의(신규 제안, PRD 보완 필요) |
| 권한 | 관리자(`accounts.role=admin`) |
| 우선순위 | MVP |
| 트리거 | API 호출(운영 콘솔) |

- **요구사항**: 검수 요청된 프로젝트를 관리자가 확인해 공개 승인하거나 반려한다.
- **처리 내용**: `status=PENDING_REVIEW`인 프로젝트만 대상. 승인 시 `funding_start_at`/`funding_deadline`을 확정하고 `status=ONGOING`으로 전환, 오픈알림신청자·팔로워에게 알림 발송. 반려 시 `status=DRAFT`로 되돌리고 사유를 판매자에게 알림 발송.
- **입력값**: `decision`(APPROVE/REJECT), `rejectReason`(REJECT 시 필수), `fundingStartAt`/`fundingDeadline`(APPROVE 시 필수)
- **출력값**: `projectId`, `status`
- **예외 처리**: `status != PENDING_REVIEW` → 409 / `fundingDeadline <= fundingStartAt` → 400
- **보안/권한**: `role != admin` → 403 · ⚠️ 관리자 콘솔 유무·수동/자동 승인 여부는 미확정, 사용자가 별도 확인 필요

---

## 리워드 관리

### PROJECT-008. 리워드 등록

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_S_PR_03_01 |
| 권한 | 판매자 본인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 프로젝트에 리워드(상품 구성)를 등록한다.
- **처리 내용**: 필수값 저장. `categoryType`이 있는데 `disclosure` 필수 항목이 비어 있어도 등록 시점엔 저장만 허용하고, 검수 요청(PROJECT-005의 `isDraft=false`) 시점에 별도 검증.
- **입력값**: `name`, `description`, `price`, `isUnlimited`, `isEarlyBird`, `simpleRefundDisabled`, `categoryType`, `disclosure`
- **출력값**: `rewardId`, `projectId`
- **예외 처리**: 필수값(리워드명·가격·무제한여부) 누락 → 400 / `price < 0` → 400
- **보안/권한**: `seller_id != CurrentUser.id` → 403

### PROJECT-009. 리워드 수정

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_S_PR_03_01 |
| 권한 | 판매자 본인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 등록된 리워드 내용을 수정한다.
- **처리 내용**: `isUnlimited`는 이 API로 수정 불가(옵션 등록 이후 재고 정책과 얽혀 있어 변경 금지, 필요 시 리워드 신규 등록). 이미 펀딩 참여가 있는 리워드의 `price` 변경은 기존 참여자에게 소급되지 않음(`funding_line_items.unit_price` 스냅샷 기준).
- **입력값**: `name`, `description`, `price`, `isEarlyBird`, `simpleRefundDisabled`, `categoryType`, `disclosure`, `displayOrder`(모두 선택)
- **출력값**: `rewardId`
- **예외 처리**: `price < 0` → 400
- **보안/권한**: `seller_id != CurrentUser.id` → 403

### PROJECT-010. 리워드 삭제

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | PRD 미정의(신규 제안) |
| 권한 | 판매자 본인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 등록된 리워드를 삭제한다.
- **처리 내용**: `rewards.deleted_at`을 채우는 소프트 삭제.
- **입력값**: `projectId`, `rewardId`(path)
- **출력값**: 처리 결과 메시지
- **예외 처리**: 해당 리워드로 이미 펀딩 참여가 1건 이상 있으면 삭제 불가 → 409
- **보안/권한**: `seller_id != CurrentUser.id` → 403

### PROJECT-011. 리워드 옵션 등록

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_S_PR_03_01 |
| 권한 | 판매자 본인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 리워드에 색상·사이즈 등 옵션을 등록한다.
- **처리 내용**: `sku`는 전체 시스템 유니크. 옵션 등록과 동시에 order-service에 `inventories` 초기화 요청(동기 호출 또는 이벤트)하여 `available_stock=initialStock` 세팅.
- **입력값**: `optionName`, `sku`, `initialStock`
- **출력값**: `rewardOptionId`
- **예외 처리**: `sku` 중복 → 409 / `initialStock < 0` → 400
- **보안/권한**: `seller_id != CurrentUser.id` → 403

### PROJECT-012. 리워드 옵션 수정

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | PRD 미정의(신규 제안) |
| 권한 | 판매자 본인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 등록된 리워드 옵션명을 수정한다.
- **처리 내용**: `sku`, 재고(`initialStock`)는 이 API로 수정 불가 — 재고는 order-service `inventories` 도메인 책임.
- **입력값**: `optionName`
- **출력값**: `rewardOptionId`
- **예외 처리**: 없음(단순 텍스트 수정)
- **보안/권한**: `seller_id != CurrentUser.id` → 403

### PROJECT-013. 리워드 옵션 삭제

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | PRD 미정의(신규 제안) |
| 권한 | 판매자 본인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 등록된 리워드 옵션을 삭제한다.
- **처리 내용**: `reward_options.deleted_at`을 채우는 소프트 삭제. 삭제 시 order-service에 재고 비활성화 이벤트 발행.
- **입력값**: `projectId`, `rewardId`, `optionId`(path)
- **출력값**: 처리 결과 메시지
- **예외 처리**: 해당 옵션으로 이미 펀딩 참여가 1건 이상 있으면 삭제 불가 → 409
- **보안/권한**: `seller_id != CurrentUser.id` → 403

### PROJECT-014. 리워드 목록 조회(옵션 포함)

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_B_PY_01_01 |
| 권한 | 비로그인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 구매자가 리워드·옵션·재고·품절 여부를 확인한다.
- **처리 내용**: 재고(`availableStock`/`soldOut`)는 order-service `inventories`를 조회해 병합. 삭제된 리워드/옵션은 응답에서 제외.
- **입력값**: `projectId`(path)
- **출력값**: 리워드 목록(옵션 포함, 재고 상태)
- **예외 처리**: 품절 옵션은 클라이언트가 [알림 신청] 버튼으로 대체 노출
- **보안/권한**: 없음(공개 API)

### PROJECT-015. 리워드 정보고시 조회

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_B_PJ_01_07 |
| 권한 | 비로그인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 전자상거래법상 상품정보제공고시를 리워드 단위로 노출한다.
- **처리 내용**: 프로젝트 내 리워드 수만큼 고시 블록 반복 노출.
- **입력값**: `projectId`(path)
- **출력값**: 리워드별 `categoryType`, `disclosure`(항목-값 쌍)
- **예외 처리**: `disclosure` 값 없는 항목은 `null` 그대로 반환, 클라이언트가 "정보 없음" 표시
- **보안/권한**: 없음(공개 API)

---

## 새소식

### PROJECT-016. 새소식 등록

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_B_PJ_01_03 |
| 권한 | 판매자 본인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 판매자가 리워드 안내/이벤트/발송정보 등 공지를 등록한다.
- **처리 내용**: `noticeType`은 사전 정의된 값(리워드안내/이벤트/제작과정/발송정보/달성률/교환환불/결제안내/FAQ)만 허용.
- **입력값**: `noticeType`, `title`, `content`
- **출력값**: `noticeId`
- **예외 처리**: `noticeType` 미정의 값 → 400
- **보안/권한**: `seller_id != CurrentUser.id` → 403

### PROJECT-017. 새소식 목록 조회

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_B_PJ_01_03 |
| 권한 | 비로그인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 사용자가 새소식을 유형별로 필터링해 확인한다.
- **처리 내용**: 기본 정렬은 최신순.
- **입력값**: `noticeType`(선택), `sort`(LATEST 기본/POPULAR)
- **출력값**: 새소식 목록
- **예외 처리**: 없음
- **보안/권한**: 없음(공개 API)

### PROJECT-018. 새소식 댓글 등록

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_B_PJ_01_03(12.3.4 댓글 기능) |
| 권한 | 로그인 회원 |
| 우선순위 | P1 |
| 트리거 | API 호출 |

- **요구사항**: 구매자가 새소식 게시물에 댓글을 작성한다.
- **처리 내용**: `content` 500자 이내 저장.
- **입력값**: `content`
- **출력값**: `commentId`
- **예외 처리**: `content` 500자 초과 → 400 / 비로그인 작성 시도 → 로그인 요청 모달
- **보안/권한**: 로그인 필수

### PROJECT-019. 새소식 댓글 목록 조회

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_B_PJ_01_03(12.3.4 댓글 기능) |
| 권한 | 비로그인 |
| 우선순위 | P1 |
| 트리거 | API 호출 |

- **요구사항**: 새소식에 달린 댓글을 확인한다.
- **처리 내용**: 최신순 정렬, 삭제된 댓글 제외.
- **입력값**: `projectId`, `noticeId`(path)
- **출력값**: 댓글 목록
- **예외 처리**: 없음
- **보안/권한**: 없음(공개 API)

---

## 커뮤니티

### PROJECT-020. 커뮤니티 목록 조회

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_B_PJ_01_04 |
| 권한 | 비로그인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 사용자가 질문/응원 게시글을 확인한다.
- **처리 내용**: `answered=false`로 미답변 질문만 필터링해 판매자가 놓치지 않도록 지원.
- **입력값**: `postType`(선택), `answered`(선택)
- **출력값**: 게시글 목록
- **예외 처리**: 없음
- **보안/권한**: 없음(공개 API)

### PROJECT-021. 질문/응원 등록

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_B_PJ_01_04 |
| 권한 | 로그인 회원(구매자) |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 사용자가 판매자에게 질문하거나 응원 글을 남긴다.
- **처리 내용**: 로그인 사용자만 작성 가능.
- **입력값**: `postType`(QUESTION/CHEER), `content`
- **출력값**: `postId`
- **예외 처리**: 비로그인 작성 시도 → 로그인 요청 모달
- **보안/권한**: 로그인 필수

### PROJECT-022. 질문 답변 등록/수정

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_S_FD_02_01 |
| 권한 | 판매자 본인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 판매자가 질문에 답변을 등록·수정한다.
- **처리 내용**: 질문당 답변은 1개(`uq_community_answers_post`) — 이미 존재하면 UPDATE로 처리. 등록/수정 시 작성자에게 온사이트 알림·보조 채널로 통지(notification-service 연동).
- **입력값**: `content`
- **출력값**: 처리 결과 메시지
- **예외 처리**: 답변 등록 실패 시 재시도 안내
- **보안/권한**: `seller_id != CurrentUser.id` → 403

---

## 서포터 후기

### PROJECT-023. 서포터 후기 목록 조회

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_B_PJ_01_05 |
| 권한 | 비로그인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 사용자가 참여자 후기를 확인한다.
- **처리 내용**: 최신순 정렬.
- **입력값**: `projectId`(path)
- **출력값**: 후기 목록
- **예외 처리**: 없음
- **보안/권한**: 없음(공개 API)

### PROJECT-024. 후기 작성

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_B_PJ_01_05 |
| 권한 | 로그인 회원(구매자) |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 펀딩 참여자가 후기를 작성한다.
- **처리 내용**: "배송완료 건만 후기 작성 가능"은 DB 제약으로 강제 불가하므로, order-service에 `fundingId`의 배송 상태를 동기 조회하거나 배송완료 이벤트를 구독해 검증.
- **입력값**: `fundingId`, `content`
- **출력값**: `reviewId`
- **예외 처리**: 배송완료 아닌 건으로 작성 시도 → 400
- **보안/권한**: 본인 소유가 아닌 `fundingId`로 요청 시 403

---

## 오픈알림 / 팔로우

### PROJECT-025. 오픈알림신청 / 취소

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_B_PJ_01_01 |
| 권한 | 로그인 회원(구매자) |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 오픈 예정 프로젝트에 알림을 신청·취소한다.
- **처리 내용**: `(project_id, member_id)` 유니크 — 중복 신청 시 idempotent하게 200 처리. 프로젝트가 `ONGOING`으로 전환(PROJECT-007 승인)되면 신청자 전원에게 알림 발송.
- **입력값**: 없음
- **출력값**: 처리 결과 메시지
- **예외 처리**: 없음
- **보안/권한**: 로그인 필수

### PROJECT-026. 프로젝트 팔로우 / 취소

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | PRD 미정의(12.3.4 "사용자 팔로우" 요구사항 반영, 신규 제안) |
| 권한 | 로그인 회원(구매자) |
| 우선순위 | P1 |
| 트리거 | API 호출 |

- **요구사항**: 사용자가 프로젝트를 팔로우해 새소식 알림을 받는다.
- **처리 내용**: `(project_id, member_id)` 유니크, 중복 요청 idempotent 처리. 새소식 등록 시 팔로워 전원에게 알림 발송.
- **입력값**: 없음
- **출력값**: 처리 결과 메시지
- **예외 처리**: 없음
- **보안/권한**: 로그인 필수 · ⚠️ `streaming.seller_follows`(판매자 팔로우)와 개념이 겹칠 수 있어 화면 정책 확인 필요

---

## 펀딩 현황

### PROJECT-027. 펀딩 현황 조회 (판매자)

| 항목 | 내용 |
| --- | --- |
| PRD 코드 | FL_S_FD_01_01 |
| 권한 | 판매자 본인 |
| 우선순위 | MVP |
| 트리거 | API 호출 |

- **요구사항**: 판매자가 목표 대비 현재 펀딩 진행상황을 확인한다.
- **처리 내용**: `wishCount`는 member-service `wishes`, `openNotifyCount`는 `project_open_notify_requests`, `rewardSales`는 order-service `funding_line_items` 집계. 갱신 주기는 **5분 캐시**로 통일(PROJECT-003의 구매자 상세조회와 동일 정책 — PRD 문구는 "실시간"/"1일 배치"로 서로 다르게 남아 있어 API 구현 기준을 우선함).
- **입력값**: `projectId`(path)
- **출력값**: `currentAmount`, `goalAmount`, `achievementRate`, `participantCount`, `wishCount`, `openNotifyCount`, `dDay`, `rewardSales`
- **예외 처리**: 없음
- **보안/권한**: `seller_id != CurrentUser.id` → 403

---

## 에러 코드 매핑

`error-handling.md` 기준으로, 공통 코드는 `CommonErrorCode`를 그대로 쓰고 새 코드만 `ProjectErrorCode`(도메인 전용, `implements ErrorCode`)에 추가합니다.

| 상황 | 코드 | 관련 항목 |
| --- | --- | --- |
| 목표금액 50만원 미만 | `CommonErrorCode.INVALID_INPUT`(400, 기존) | PROJECT-004 |
| 개인정보 처리 동의 안 함 | `ProjectErrorCode.PRIVACY_NOT_AGREED`(신규, 400) | PROJECT-004 |
| 카테고리 대/소분류 조합이 마스터 목록에 없음 | `ProjectErrorCode.INVALID_CATEGORY`(신규, 400) | PROJECT-004 |
| 상세페이지 제목 40자 초과 | `CommonErrorCode.INVALID_INPUT`(400, 기존) | PROJECT-005 |
| 검수 요청(`isDraft=false`) 시 필수 항목(제목/대표이미지/소개/리워드) 누락 | `CommonErrorCode.INVALID_INPUT`(400, 기존) | PROJECT-005 |
| 이미 검수 요청되어 `PENDING_REVIEW`인 프로젝트에 검수를 재요청 | `CommonErrorCode.CONFLICT`(409, 기존) | PROJECT-005 |
| `PENDING_REVIEW` 상태 프로젝트의 기본정보·상세페이지·리워드·옵션을 수정 시도 | `CommonErrorCode.RESOURCE_LOCKED`(423, 기존) | PROJECT-004, PROJECT-005, PROJECT-009, PROJECT-012 |
| `PENDING_REVIEW`/`ONGOING` 상태 프로젝트 삭제 시도 | `ProjectErrorCode.PROJECT_NOT_DELETABLE`(신규, 422) | PROJECT-006 |
| 리워드 가격 음수 / 초기 재고 음수 | `CommonErrorCode.INVALID_INPUT`(400, 기존) | PROJECT-008, PROJECT-009, PROJECT-011 |
| 리워드 옵션 `sku` 중복 | `ProjectErrorCode.DUPLICATE_SKU`(신규, 409) | PROJECT-011 |
| 이미 펀딩 참여 이력이 있는 리워드/옵션 삭제 시도 | `ProjectErrorCode.REWARD_HAS_ACTIVE_FUNDING`(신규, 409) | PROJECT-010, PROJECT-013 |
| 검수 승인/반려(`PATCH /review`)를 `PENDING_REVIEW`가 아닌 프로젝트에 호출 | `ProjectErrorCode.PROJECT_REVIEW_NOT_PENDING`(신규, 409) | PROJECT-007 |
| 검수 승인인데 `fundingStartAt`/`fundingDeadline` 누락, 또는 마감일이 시작일보다 빠름 | `CommonErrorCode.INVALID_INPUT`(400, 기존) | PROJECT-007 |
| 검수 반려인데 `rejectReason` 누락 | `CommonErrorCode.INVALID_INPUT`(400, 기존) | PROJECT-007 |
| 관리자가 아닌 사용자가 검수 승인/반려 호출 | `CommonErrorCode.FORBIDDEN`(403, 기존) | PROJECT-007 |
| 새소식 `noticeType`이 사전 정의된 값이 아님 | `CommonErrorCode.INVALID_INPUT`(400, 기존) | PROJECT-016 |
| 새소식 댓글 500자 초과 | `CommonErrorCode.INVALID_INPUT`(400, 기존) | PROJECT-018 |
| 비로그인 상태로 댓글·질문/응원·팔로우·오픈알림신청 시도 | `CommonErrorCode.UNAUTHORIZED`(401, 기존) | PROJECT-018, PROJECT-021, PROJECT-025, PROJECT-026 |
| 배송완료 되지 않은 건으로 후기 작성 시도 | `ProjectErrorCode.SUPPORTER_REVIEW_NOT_ELIGIBLE`(신규, 422) | PROJECT-024 |
| 본인 소유가 아닌 `fundingId`로 후기 작성 시도 | `CommonErrorCode.FORBIDDEN`(403, 기존) | PROJECT-024 |
| 판매자 본인이 아닌 사용자가 프로젝트/리워드/옵션/새소식/답변/펀딩현황 API 호출 | `CommonErrorCode.FORBIDDEN`(403, 기존) | PROJECT-002, 004~013, 015~017, 022, 027 |
| 존재하지 않는 `projectId`/`rewardId`/`optionId`/`noticeId` 조회 | `CommonErrorCode.NOT_FOUND`(404, 기존) | 전체 상세 조회형 API 공통 |
| `DRAFT`/`PENDING_REVIEW` 프로젝트를 소유자가 아닌 사용자가 상세조회 시도 | `CommonErrorCode.NOT_FOUND`(404, 기존) — 존재 여부 비노출을 위해 403이 아닌 404로 응답 | PROJECT-003 |
| 재고 초기화·재고 비활성화 등 order-service 연동 실패 | `CommonErrorCode.DEPENDENCY_FAILURE`(503, 기존) | PROJECT-011, PROJECT-013, PROJECT-024 |
# catalog-service

루트 `CLAUDE.md`(레포 공통 규칙)와 `.claude/rules/`를 전제로, 여기는 **catalog-service(project 도메인)**에만 해당하는 내용만 다룹니다.

## 이 서비스가 하는 일

프로젝트(펀딩 프로젝트) 생성·관리, 검수, 리워드/리워드옵션, 새소식, 커뮤니티(질문/응원/답변), 서포터 후기, 오픈알림신청, 프로젝트 팔로우 소유. (PRD.md 3~5, 12장 기준)

재고(가용재고·품절 판정)·펀딩 참여·결제·쿠폰·환불은 order-service 소관입니다 — 이 서비스는 `reward_options`까지만 갖고 있고, 재고 수량은 직접 저장하지 않습니다. 회원 프로필·사업자정보·본인인증은 member-service 소관입니다 — 이 서비스는 `seller_id`/`member_id`를 UUID 참조값으로만 들고 있고(FK 아님), 프로필 데이터를 직접 보관하지 않습니다.

## 먼저 읽을 문서

프로젝트/리워드 관련 작업을 시작하기 전에 `services/catalog-service/docs/project-functional-spec.md`를 먼저 읽으세요 — PROJECT-001~027 기능별 요구사항, 예외 처리, 에러 코드 매핑표가 있습니다. 요청/응답 스키마 상세는 `project-api-spec.md`를 참고하세요. 이 `CLAUDE.md`는 두 문서의 핵심만 요약한 것이지 대체하지 않습니다.

**확인 상태**: 카테고리 마스터 테이블화, 재고 단위(옵션별 개별 수량) 확정, `PENDING_REVIEW` 상태 도입, 소프트 삭제(`deleted_at`), 새소식 댓글/프로젝트 팔로우 스키마, 에러 코드 매핑까지 ERD·API 스펙·기능명세서에 모두 반영 완료. 남은 건 아래 세 가지, PRD 보완 및 정책 확정은 사용자가 별도 진행하기로 함:
- `PATCH /review`(검수 승인/반려)의 실제 운영 주체 — 관리자 수동 승인으로 우선 구현. PRD에는 이 절차 자체가 없어 PRD 보완 필요.
- 펀딩 현황 갱신 주기 — 구현은 5분 캐시로 통일했으나, PRD 12.1.3/12.1.4("실시간")와 PRD 7.2.3("1일 배치") 문구는 그대로 남아 있음. 코드는 5분 캐시 기준으로 작성할 것.
- `project_follows`(신규)와 `streaming.seller_follows`(기존, 판매자 팔로우)의 통합 여부 — 현재는 별도 테이블. 화면 정책 확정되면 합쳐질 수 있음.

## 도메인 테이블 (스키마 확정됨)

- **`projects`** — `id`(BIGINT PK), `public_id`(UUID v7, 외부 노출용), `seller_id`(UUID, member-service 참조), `category_major`/`category_minor`(`categories` 복합 FK), `title`, `goal_amount`(CHECK ≥500000), `funding_start_at`/`funding_deadline`(NULLABLE — 검수 승인 시 확정), `status`(`DRAFT`/`PENDING_REVIEW`/`ONGOING`/`SUCCEEDED`/`FAILED`), `deleted_at`.
- **`categories`** — `(category_major, category_minor)` 복합 PK 마스터 테이블. PRD 4.2.4 목록을 시드 데이터로 적재, `projects`가 이 조합만 저장 가능하도록 FK로 강제.
- **`project_review_requests`** — 검수 요청 이력(`SUBMITTED`/`APPROVED`/`REJECTED`, `reject_reason`, `reviewer_id`, `submitted_at`/`reviewed_at`). 승인 시점에 `projects.funding_start_at`/`funding_deadline`을 확정.
- **`rewards`** — `project_id` FK, `price`(CHECK ≥0), `is_unlimited`, `is_early_bird`, `simple_refund_disabled`, `category_type`/`disclosure`(전자상거래법 고시용 JSONB), `deleted_at`. `is_unlimited=false`의 "제한 수량"은 옵션(`reward_options`) 단위 개별 수량으로 확정됨.
- **`reward_options`** — `reward_id` FK, `sku`(전체 시스템 유니크), `option_name`, `deleted_at`. 재고 수량 컬럼 없음(order-service `inventories` 참조).
- **`project_notices`** — `noticeType`(사전 정의 값만 허용), `title`, `content`.
- **`project_notice_comments`** — `notice_id` FK, `member_id`, `content`(500자 이내), `deleted_at`.
- **`project_follows`** — `(project_id, member_id)` 유니크. 새소식 등록 시 팔로워 전원에게 알림 발송.
- **`community_posts` / `community_answers`** — `post_type`(`QUESTION`/`CHEER`), 질문당 답변 1개(`uq_community_answers_post`) — 답변은 POST로 등록/수정 겸용(있으면 UPDATE).
- **`reviews`** — 배송완료 여부는 order-service 조회로 검증(DB 제약으로 강제 불가).
- **`project_open_notify_requests`** — `(project_id, member_id)` 유니크, 프로젝트 `ONGOING` 전환 시 신청자 전원에게 알림.

## 핵심 설계 결정 (구현 시 반드시 지킬 것)

- **상태 전이는 `DRAFT → PENDING_REVIEW → (ONGOING | DRAFT)` 한 방향으로만**: `detail` PATCH에 `isDraft=false`를 보내면 `PENDING_REVIEW`로 전환하고, 이 상태에서는 `basic-info`/`detail`/리워드·옵션 수정 API를 전부 423(`RESOURCE_LOCKED`)으로 막는다. 승인(`ONGOING`)·반려(`DRAFT` 복귀)는 오직 `PATCH /review`로만 바뀐다 — 다른 API에서 `status`를 직접 바꾸지 않는다.
- **카테고리는 반드시 `categories` 마스터 테이블 조합만 허용**: `basic-info` 저장 시 자유 문자열을 그대로 받지 말고 `(category_major, category_minor)` 조합이 `categories`에 존재하는지 검증한다.
- **재고는 이 서비스가 소유하지 않는다**: `reward_options` 등록/수정/삭제 시 재고 자체를 이 서비스 DB에 쓰지 말고, 반드시 order-service 호출(또는 이벤트)로 위임한다. 재고 조회(`availableStock`/`soldOut`)도 매번 order-service 병합이 필요하다.
- **삭제는 전부 소프트 삭제**: `rewards`/`reward_options`/`project_notice_comments` 모두 `deleted_at`으로 처리하고, 삭제된 리워드/옵션/댓글은 목록 조회에서 반드시 필터링한다. 이미 펀딩 참여(`funding_line_items`)가 있는 리워드/옵션은 하드는 물론 소프트 삭제도 막아야 한다(409).
- **`sku`는 전역 유니크**: `reward_options.sku` 중복 시 409, 재시도 유도 문구 필요(신규 등록/수정 공통).
- **`public_id`(UUID v7)만 외부에 노출**: 모든 API 경로 파라미터와 응답의 `projectId`는 `public_id`이지 내부 BIGINT `id`가 아니다. 서비스 간 참조(catalog↔order 등)는 BIGINT `id` 기준.
- **비공개 프로젝트는 403이 아닌 404로 존재 비노출**: `DRAFT`/`PENDING_REVIEW` 프로젝트를 소유자가 아닌 사용자가 조회하면 404로 응답한다(anti-enumeration, auth의 계정 존재 비노출과 동일한 원칙).
- **캐시 정책 5분 통일**: 구매자 상세조회의 `currentAmount`/`achievementRate`/`participantCount`와 판매자 `funding-summary`는 동일하게 5분 캐시로 구현한다(한쪽만 실시간으로 만들지 않는다).

## 에러 코드

도메인 전용 코드는 `ProjectErrorCode implements ErrorCode`로 만든다(서비스당 flat enum 1개 — `error-handling.md` 컨벤션). 정확히 어떤 코드가 필요한지는 `project-functional-spec.md`의 "에러 코드 매핑" 표가 기준이다 — 새로 만들기 전에 그 표부터 확인할 것 (예: `PRIVACY_NOT_AGREED`, `INVALID_CATEGORY`, `DUPLICATE_SKU`, `REWARD_HAS_ACTIVE_FUNDING`, `PROJECT_REVIEW_NOT_PENDING`, `PROJECT_NOT_DELETABLE`, `SUPPORTER_REVIEW_NOT_ELIGIBLE`). `INVALID_INPUT`/`UNAUTHORIZED`/`FORBIDDEN`/`NOT_FOUND`/`CONFLICT`/`RESOURCE_LOCKED`/`DEPENDENCY_FAILURE`는 이미 `CommonErrorCode`에 있으니 재정의하지 않는다.

`PROJECT_REVIEW_NOT_PENDING`(프로젝트 검수)과 `SUPPORTER_REVIEW_NOT_ELIGIBLE`(서포터 후기)은 이름이 헷갈리기 쉬우니 접두어(`PROJECT_REVIEW_` / `SUPPORTER_REVIEW_`) 그대로 유지할 것.

## 이 서비스에서 절대 하지 말아야 할 것

- `reward_options`에 재고 수량 컬럼을 새로 만들거나, 재고 차감 로직을 catalog-service DB 트랜잭션 안에 넣지 말 것 — order-service `inventories`가 source of truth
- `PENDING_REVIEW` 상태의 프로젝트를 `basic-info`/`detail`/`rewards`/옵션 어떤 API로도 수정 가능하게 열어두지 말 것
- `projects.status`를 `PATCH /review` 이외의 엔드포인트에서 직접 변경하지 말 것
- `category_major`/`category_minor`를 `categories` 마스터 테이블 검증 없이 자유 문자열로 저장하지 말 것
- 이미 펀딩 참여가 있는 `rewards`/`reward_options`를 하드 삭제하거나, 검증 없이 소프트 삭제 처리하지 말 것
- `DRAFT`/`PENDING_REVIEW` 프로젝트 비공개 응답에서 403과 404를 섞어 쓰지 말 것 (존재 비노출 원칙 위반)
- 구매자용 상세조회와 판매자용 `funding-summary`의 캐시 정책을 서로 다르게(한쪽만 실시간) 구현하지 말 것
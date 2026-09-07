# project-service

> 루트 `CLAUDE.md`(레포 공통 규칙)와 `.claude/rules/`를 전제로, 여기는 project-service에만 해당하는 내용만 다룹니다.

## 이 서비스가 하는 일
프로젝트, 리워드/옵션/고시정보, 커뮤니티, 새소식, 후기, 프로젝트 심사. (`PRD.md` 2. 도메인/서비스 개요 기준)

LIVE 방송 송출 자체는 live-service 소관입니다. 이 서비스는 방송이 끝난 뒤 남는 LIVE검증 콘텐츠와 펀딩스토리 AI 초안을 보관합니다.

주문·재고 차감·목표 달성 판정은 order-service 소관입니다. project-service는 리워드 정의와 재고 조회를 제공하고, 펀딩 현황은 order-service를 호출해 가져옵니다.

## 먼저 읽을 문서
프로젝트/리워드 관련 작업을 시작하기 전에 **`services/project-service/docs/`의 `ProjectDomainFunctionalSpec.md`(PROJECT-001~030)와 `ProjectDomainApiSpec.md`를 먼저 읽으세요.** 이 CLAUDE.md는 그 문서들의 핵심만 요약한 것이지 대체하지 않습니다.

## 로컬 실행
- 앱 포트: `8083` (`application-local.yml`, gitignore 대상 — 커밋하지 않음)
- DB 포트: `5434`
- 최초 한 번: `.env.example`을 `.env`로 복사한 뒤 `docker compose up -d`

```bash
cp services/project-service/.env.example services/project-service/.env
cd services/project-service && docker compose up -d
```

## 도메인 테이블 (스키마 — MVP 범위)

- categories — category_major/category_minor 복합 PK, display_order. 프로젝트 생성 시 참조하는 마스터 데이터(시드 데이터로 채움, 별도 CRUD API 없음).
- projects — id(BIGINT), public_id(UUID v7, 외부 노출용 — URL/API 경로 파라미터는 전부 이 값), seller_id(UUID, member-service members.id 참조, FK 아님 — 서비스 분리), business_type(GENERAL/SOLE/CORP), category_major/category_minor(FK), title(40자 제한), goal_amount(50만원 이상 CHECK), funding_start_at/funding_deadline(둘 다 nullable — 심사 승인 시점에만 확정, 그 전엔 NULL), status(DRAFT/PENDING_REVIEW/ONGOING/SUCCEEDED/FAILED), deleted_at(소프트 딜리트), project_display_code(GENERATED ALWAYS AS 'F' || LPAD(id,7,'0') — 애플리케이션에서 직접 세팅하지 않음).
- project_review_requests — 심사 이력(append 성격). status(SUBMITTED/APPROVED/REJECTED), reviewer_id(UUID, role=ADMIN인 accounts 참조, FK 아님), reject_reason.
- project_open_notify_requests — 오픈 예정 프로젝트 알림 신청. (project_id, member_id) 유니크(중복 신청 방지).
- rewards — project_id(FK), price, is_limited/quantity(is_limited=true면 quantity 필수·0 이상, false면 quantity는 반드시 NULL — DB CHECK로 강제), has_option, disclosure(JSONB, 품목 유형별 법정고시), simple_refund_disabled, deleted_at(소프트 딜리트, 삭제된 리워드는 소비자 응답에서 제외), reward_display_code(GENERATED, R0000001 형식).
- reward_option_groups/reward_option_values — has_option=true인 리워드에만 존재하는 2단 구조(그룹: 색상 등 / 값: 화이트·블랙 등).
- community_posts/community_answers — post_type(QUESTION/CHEER). 답변은 게시글당 1개(uq_community_answers_post 유니크) — 답변 등록 API는 생성이 아니라 UPSERT로 구현.
- project_notices/project_notice_comments — 새소식(공지)과 댓글. 댓글은 500자 제한, 소프트 딜리트.

스키마에서 제외/보류:
- project_follows — SQL 상에는 존재하나(프로젝트 단위 팔로우), 기능명세서(PROJECT-XXX)상 "메이커 팔로우/언팔로우"는 MEMBER-007로 member-service 담당으로 명시되어 있어 소유권이 서로 다르게 되어 있음. 재검토 전까지 이 테이블을 사용하는 API는 만들지 않는다.
- reviews — SQL 상에는 존재하나, PROJECT-001~030 어디에도 후기 작성/조회 API가 정의돼 있지 않음(order-service 쪽 "배송완료 후 후기 작성 화면 이동" 언급만 있음). MVP 범위 밖 — 별도 이슈로 진행, 이번 슬라이스들에서 이 테이블을 건드리지 않는다.
- inventories — 재고 수량 원장(inventories)은 이 서비스 스키마에 없다 — order-service 소관(rewards.quantity는 판매자가 설정한 한도값일 뿐, 실시간 잔여재고는 order-service를 호출해서 가져온다).

## 핵심 설계 결정 (구현 시 반드시 지킬 것)

- 소유권 검증은 모든 쓰기 API의 전제조건: projects.seller_id/리워드가 속한 프로젝트의 seller_id가 로그인 회원과 일치하는지 서버에서 항상 대조한다(security.md S4). 식별자만으로 접근하지 않는다 — 불일치 시 CommonErrorCode.FORBIDDEN.
- display_code류 컬럼은 애플리케이션에서 세팅하지 않는다: project_display_code/reward_display_code는 DB GENERATED ALWAYS AS ... STORED 컬럼이다. INSERT 시 값을 지정하려 하지 말 것.
- funding_start_at/funding_deadline은 생성 시점에 확정하지 않는다: 심사 승인(PROJECT-030) 처리 로직에서만 값을 채운다. 그 전 단계 API에서 이 값을 요구하거나 임의로 세팅하지 않는다.
- 리워드 수량 변경은 order-service에 동기화 이벤트가 필요하다: rewards.quantity를 생성/수정하면 order-service의 재고 원장(inventories)에 반영되도록 이벤트를 발행한다(최종적 일관성 — 동기 호출로 강결합하지 않는다).
- 삭제는 전부 소프트 딜리트: projects/rewards/project_notice_comments 모두 deleted_at으로 처리한다. 하드 삭제(물리 DELETE)는 사용하지 않는다.
- 커뮤니티 답변은 UPSERT: 게시글당 답변은 1개(DB 유니크 제약)이므로, 답변 등록 API를 호출할 때마다 새로 만들지 말고 기존 답변이 있으면 갱신한다.
- categories는 읽기 전용 마스터 데이터: 프로젝트 생성/수정 시 존재 여부만 검증(FK)하고, project-service가 카테고리를 생성·수정하는 API는 만들지 않는다(시드 데이터로만 관리).

## 에러 코드

도메인 전용 코드는 ProjectErrorCode implements ErrorCode로 만든다(서비스당 flat enum 1개 — error-handling.md 컨벤션). INVALID_INPUT/UNAUTHORIZED/FORBIDDEN/NOT_FOUND/DEPENDENCY_FAILURE는 이미 CommonErrorCode에 있으니 재정의하지 않는다.

전체 매핑은 docs/ProjectDomainErrorCodeMapping.md가 기준이다. MVP 범위(프로젝트 생성/관리 슬라이스)에서 실제로 쓰는 신규 코드만 요약하면:

| 코드 | HTTP | 상황 |
| :--- | :--- | :--- |
| GOAL_AMOUNT_TOO_LOW | 400 | 목표금액 50만원 미만 |
| INVALID_CATEGORY | 400 | 존재하지 않는 카테고리 조합 |
| PRIVACY_CONSENT_REQUIRED | 422 | 개인정보 수집 동의 없이 다음 단계 진행 |
| PROJECT_NOT_DELETABLE | 422 | DRAFT가 아닌 프로젝트 삭제 시도 |
| PROJECT_NOT_SUBMITTABLE | 422 | 필수 작성 항목 미완료 상태로 심사 제출 |

나머지(리워드/AI/LIVE검증 관련 신규 코드)는 해당 슬라이스 구현 시점에 추가한다.

## 이 서비스에서 절대 하지 말아야 할 것

- 클라이언트가 보낸 sellerId/projectId 소유자 정보를 신뢰하지 말 것 — 항상 로그인 회원(@CurrentMember/X-Account-Id)과 서버에서 대조
- DRAFT가 아닌 프로젝트의 삭제를 허용하지 말 것 (PROJECT_NOT_DELETABLE)
- 개인정보 수집 동의 없이 심사 제출 등 다음 단계로 진행시키지 말 것 (PRIVACY_CONSENT_REQUIRED)
- 미공개(DRAFT/PENDING_REVIEW) 프로젝트를 공개 상세 API에서 404가 아닌 다른 코드로 구분하지 말 것 (존재 여부 비노출)
- project_display_code/reward_display_code를 애플리케이션 코드에서 직접 생성/세팅하지 말 것 — DB GENERATED 컬럼
- 재고 수량(잔여재고)을 이 서비스 DB에 원장으로 두거나 자체 계산하지 말 것 — order-service 조회 결과를 그대로 쓸 것
- project_follows/reviews 테이블을 사용하는 API를 이번 슬라이스에 슬쩍 포함시키지 말 것 — 소유권 재검토·범위 미정 (MvpImplementationSummary.md에서 먼저 확인)
- 카테고리(categories)를 생성/수정하는 API를 만들지 말 것 — 읽기 전용 마스터 데이터
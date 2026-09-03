# member-service

> 루트 `CLAUDE.md`(레포 공통 규칙)와 `.claude/rules/`를 전제로, 여기는 member-service에만 해당하는 내용만 다룹니다.

## 이 서비스가 하는 일
회원 프로필, 구매자/판매자 접근(별도 모드를 지정하지 않고 가입 시 양쪽 모두 자동 부여), 약관동의(이력 보존), 찜, 배송지 소유. (`PRD.md` 2. 도메인/서비스 개요 기준)

로그인 자격증명과 본인인증(CI/DI 포함) 처리는 전부 auth-service 소관입니다. member-service는 auth-service가 회원가입 시 동기 호출할 때 넘겨주는 값(accountId, name, phoneNumber 등)을 받아 프로필로 저장만 합니다 — `phone_number`는 본인인증 성공 여부와 무관하게 입력값 그대로 저장하며, CI/DI 등 인증 관련 필드는 member-service가 갖지 않습니다(auth-service도 현재 미사용 상태 — `IdentityVerificationStore.VerifiedIdentity`에 필드 없음).

팔로우, 리워드 품절 알림 신청, 닉네임 수정, 소셜가입은 MVP 범위 밖입니다 — 상세는 `MvpImplementationSummary.md` 참고.

## 먼저 읽을 문서
회원가입/프로필/찜/배송지 관련 작업을 시작하기 전에 **`services/member-service/docs/`의 기능명세서(MEMBER-001~009)와 `member-domain-api-spec.md`를 먼저 읽으세요.** 이 CLAUDE.md는 그 문서들의 핵심만 요약한 것이지 대체하지 않습니다. **MVP 범위에서 제외된 항목(후순위 구현)은 `MvpImplementationSummary.md`를 참고하세요** — 기능명세서/API명세서에 항목 자체는 남아있어도 이번 구현 범위엔 포함되지 않습니다.

> 확인 상태: 스키마·기능명세서·API명세서 간 불일치로 지적됐던 항목은 모두 정리됐습니다(사용자 확인 완료, 2026-09-03). 구매자/판매자 모드는 별도 컬럼 없이 처리(세션/토큰 클레임 수준), 본인인증 관련 필드는 전부 auth-service로 이관, 판매자 심사 관련 필드는 member-service 소관이 아니므로 스키마에서 제외했습니다. 남은 건 AUTH-009 연동용 내부 조회 엔드포인트 스펙 작성뿐이며, 이마저 후순위입니다(`MvpImplementationSummary.md` 참고).

## 도메인 테이블 (스키마 — MVP 범위)
- `members` — `id`(UUID, auth-service `accounts.id`를 그대로 사용, FK 아님 — 서비스 분리), `name`, `phone_number`(본인인증 성공 여부와 무관하게 입력값 그대로 저장), `nickname`(nullable, 수정 API는 MVP 범위 밖), `created_at`, `updated_at`, `deleted_at`(소프트 딜리트 — 탈퇴 플로우 자체는 아직 스펙 없음, 쓰는 경로 미정).
    - **스키마에서 제외**: `is_foreigner`/`di_hash`/`phone_verified_at`(본인인증·CI/DI 관련, auth-service로 이관 — auth-service도 현재 미사용), `business_type`/`business_info`/`seller_verified_at`(판매자 심사 관련, member-service 소관 아님), `current_mode`(구매자/판매자를 별도 지정하지 않는 설계라 저장 자체가 불필요 — `GET /members/me`의 `currentMode` 응답은 세션/토큰 클레임에서 파생되는 값이며, 이 사실을 API 명세서에도 명시해둘 것).
- `terms_agreements` — 약관 동의 append-only 이력(법적 보존 목적, UPDATE/DELETE 없음). `terms_type`은 `SERVICE_USE`/`PRIVACY`/`AGE_OVER_14`/`MARKETING`/`AI_PERSONALIZATION`.
- `wishes` — `project_title`/`project_thumbnail_url`은 catalog-service 이벤트 구독으로 동기화하는 스냅샷(비정규화, 최종적 일관성). `member_id`는 FK, `project_id`는 FK 아님(타 서비스 참조).
- `addresses` — 회원당 다건 등록 가능.

## 핵심 설계 결정 (구현 시 반드시 지킬 것)
- **내부 전용 엔드포인트 방어**: `POST /members`, `POST /members/social`은 게이트웨이 라우팅에서 제외 + `X-Internal-Api-Key` 헤더로 auth-service만 호출 가능하도록 검증한다(`security.md` S7). `accountId`는 auth-service가 발급한 값이라는 전제로 신뢰하고 별도 검증하지 않는다.
- **구매자/판매자는 별도로 지정하지 않는다**: 가입 완료 시 둘 다 자동 부여되며, member-service는 이를 위한 별도 저장 상태를 갖지 않는다. 화면 전환이 필요하면 세션/토큰 클레임 수준에서만 다룬다 — `members` 테이블에 모드 값을 영속화하지 않는다.
- **찜은 idempotent**: 등록은 `PUT`(`INSERT ... ON CONFLICT DO NOTHING`), 해제는 `DELETE`(이미 목표 상태면 204). 중복 요청·네트워크 재시도를 실패로 처리하지 않는다.
- **찜 목록의 프로젝트 정보는 스냅샷**: catalog-service를 실시간 호출하지 않는다.
- **회원가입 실패 시 보상 트랜잭션은 auth-service 책임**: member-service는 생성 실패 시 auth-service에 실패 응답만 반환하면 된다. 자체적으로 롤백/삭제 로직을 만들지 않는다(auth-service CLAUDE.md의 "회원가입 보상 트랜잭션" 참고).
- **본인인증(CI/DI) 데이터는 갖지 않는다**: `phone_number`만 입력값으로 저장하고, 인증 여부·CI/DI 관련 로직은 전부 auth-service 소관이다. auth-service가 향후 CI/DI를 저장하게 되면(현재는 미사용) 그때 member-service 반영 여부를 다시 결정한다.

## 에러 코드
도메인 전용 코드는 `MemberErrorCode implements ErrorCode`로 만든다(서비스당 flat enum 1개 — `error-handling.md` 컨벤션). `INVALID_INPUT`/`UNAUTHORIZED`/`NOT_FOUND`/`DEPENDENCY_FAILURE`는 이미 `CommonErrorCode`에 있으니 재정의하지 않는다. MVP 범위에서 member-service 고유의 도메인 에러 코드는 현재 없음(팔로우 관련 코드 등은 팔로우 기능 자체가 후순위라 함께 후순위).

## 이 서비스에서 절대 하지 말아야 할 것
- 비밀번호/자격증명을 저장하거나 조회하지 말 것 — `members` 테이블엔 애초에 그런 컬럼이 없음, 그건 auth-service 소관
- CI/DI 등 본인인증 관련 데이터를 저장하지 말 것 — auth-service 소관이며 현재 auth-service도 미사용 상태
- 구매자/판매자 모드를 `members` 테이블에 영속화하려 하지 말 것 — 설계상 별도 저장이 필요 없음
- 찜 목록 조회 시 catalog-service를 매번 실시간 호출하지 말 것 — 스냅샷 컬럼을 사용
- 배송지 등 개인정보를 평문으로 로그에 남기지 말 것 (`security.md` S9·S10)
- `POST /members`, `POST /members/social`을 게이트웨이 라우팅에 노출하지 말 것 — auth-service 내부 호출 전용
- MVP 범위 밖 기능(팔로우, 리워드알림, 닉네임 수정, 소셜가입)을 별다른 논의 없이 구현 범위에 슬쩍 포함시키지 말 것 — `MvpImplementationSummary.md`에서 먼저 확인
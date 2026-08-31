# MVP 구현 요약 (AUTH-001, 003, 005, 006, 007, 011)

> 이 문서는 이번 구현 세션에서 실제로 만든 것·바꾼 것·남은 것을 검토하기 위한 요약이다. 상세 계획은 `/Users/jaesung/.claude/plans/services-auth-service-docs-auth-function-imperative-whisper.md`(로컬 플랜 파일), 기능 요구사항은 `AuthFunctionalSpec.md`/`AuthDomainApiSpec.md` 참고.

## 범위 변경 (원래 계획 대비)

- ~~AUTH-004/005(휴대폰 인증번호 발송/검증)는 이번 슬라이스에서 제외~~ **→ 2026-08-31 추가 세션에서 해소됨**: 벤더가 PortOne 통합인증으로 확정되면서 AUTH-005(본인인증 결과 조회)를 구현하고 AUTH-004는 폐기했다. 하단 "PortOne 통합인증 연동" 절 참고.
- ~~AUTH-007(회원가입)의 `verificationToken` 필드는 요청 바디로 받되 검증하지 않는다~~ **→ 2026-08-31 해소됨**: `SignupService`에 `verificationToken`→`phoneNumber` 대조 로직 추가 완료.
- **인증 방식은 커스텀 필터가 아니라 Spring Security(`spring-boot-starter-security`)**를 쓴다(작업 중 사용자 확정). Redis 의존성은 빼고, `spring-security-crypto` 대신 `spring-boot-starter-security` 전체를 사용.
- **AUTH-001 로그인 응답에서 `member.nickname`을 생략**한다(`AuthDomainApiSpec.md` 문서 스펙과 의도적으로 다름) — member-service가 아직 레포에 없어 로그인 경로에 불필요한 동기 결합을 만들지 않기 위함(사용자 확정). 응답은 `{accessToken, mustChangePassword}`만.
- **Refresh Token은 opaque 값이 아니라 JWT**다 — `AuthDomainApiSpec.md` AUTH-003 설명 중 "서명은 유효하지만 DB에 없는 토큰의 account_id 클레임 사용"이라는 문구를 근거로 확정. 클레임: `jti`=`refresh_tokens.token_id`, `sub`=`account_id`.
- **`V1__init_schema.sql`의 `set_updated_at()` 트리거 참조**는 애초에 정의되지 않은 함수를 가리키고 있어(버그) 마이그레이션이 실패하는 상태였다. 처음엔 `V0__create_functions.sql`로 함수를 추가하는 방식을 계획했으나, **사용자가 직접 V1에서 트리거를 제거**하고 `updated_at` 갱신을 JPA 엔티티(`@PreUpdate`)가 담당하는 방향으로 확정함(`persistence-convention.md` 예시와 동일 패턴). V0 마이그레이션은 만들지 않았다.

## Phase별 내역

### Phase 0 — 스캐폴딩/설정
- `build.gradle`: `modules:common` → `modules:common-webmvc`(중복 선언 없이 `spring-boot-starter-web`도 같이 딸려옴), `spring-boot-starter-security`, `jjwt-{api,impl,jackson}:0.12.6` 추가. 테스트용 `spring-boot-starter-webmvc-test` 추가(→ Phase 6에서 필요해짐).
- `AuthServiceApplication.java`(사용자가 직접 작성 후, `public static void main` 누락 버그를 발견해 수정).
- `application.yml`(공통, `server.port: 8080`), `application-local.yml`(gitignore, 포트 8081 + 로컬 DB/JWT 시크릿), `application-dev.yml`/`application-prod.yml`(env var 참조).
- `docker-compose.yml` + `.env.example`(Postgres만, Redis 없음).

### Phase 1 — 마이그레이션
- 위 "범위 변경" 참고. 추가 파일 없음, `V1__init_schema.sql`은 사용자가 직접 수정.

### Phase 2 — domain
- `AuthErrorCode`(`INVALID_CREDENTIALS`/`ACCOUNT_LOCKED`/`EMAIL_ALREADY_EXISTS` 3개), `Account`(복잡한 애그리거트 — 잠금/실패카운트 상태 전이를 도메인 메서드로), `Role`, `AccountRepository`(포트), `AccountLockedException`.

### Phase 3 — infrastructure
- `persistence/account/*`(JpaEntity/JpaRepository/Mapper/PersistenceAdapter, `persistence-convention.md` §1 패턴), `persistence/refreshtoken/*`(단순 애그리거트, §2 패턴 — Mapper/Adapter 없이 JpaEntity를 application이 직접 사용).
- `security/JwtTokenProvider`(Access+Refresh JWT 발급/검증), `security/JwtProperties`, `security/JwtAuthenticationFilter`, `security/SecurityConfig`(SecurityFilterChain, PasswordEncoder, 커스텀 401 응답 포맷).
- `member/MemberServiceRestClient`(포트 구현, `RestClient` + 3초 타임아웃 명시 설정 — CLAUDE.md 규칙).
- `presentation/GlobalExceptionHandler`(2줄 기본형 + `AccountLockedException` 전용 핸들러 1개 추가 — 잠금 해제 시각을 응답에 실어야 하는데 공용 `AbstractGlobalExceptionHandler`가 detail을 안 실어줘서, `modules:common-webmvc`는 건드리지 않고 여기서만 확장).

### Phase 4 — application
- `LoginService`(AUTH-001), `TokenRefreshService`(AUTH-003, 회전+재사용탐지), `EmailAvailabilityService`(AUTH-006), `SignupService`(AUTH-007, 보상 트랜잭션), `PasswordChangeService`(AUTH-011).
- `TokenIssuer`: 로그인/회원가입/토큰재발급 3곳에서 "Access+Refresh 발급 후 refresh_tokens 저장" 로직이 동일해서 공용 컴포넌트로 뽑음.
- **보상 트랜잭션 구현 방식**: `SignupService.signup()` 전체를 `@Transactional`로 감싸지 않았다 — `accountRepository.save()` 호출 자체가(Spring Data JPA의 기본 동작상) 독립 트랜잭션으로 즉시 커밋되고, 그 다음에 member-service를 호출한다. 실패 시 별도로 `deleteById()`. 이게 CLAUDE.md가 요구하는 "롤백이 아니라 보상 트랜잭션" 패턴의 핵심.

### Phase 5 — presentation
- `AuthController`(5개 엔드포인트: check-email, signup, login, token/refresh, PATCH password), 요청/응답 DTO, `RefreshTokenCookieFactory`(쿠키 조립 공용 헬퍼), `ValidPassword`/`PasswordComplexityValidator`(커스텀 Bean Validation — 스펙에 구체 기준이 없어 최소 8자+3종류 이상으로 가정).

### Phase 6 — 테스트
- 단위/예외 테스트: `LoginService`, `TokenRefreshService`, `SignupService`, `PasswordChangeService`, `EmailAvailabilityService`, `Account`(도메인), `AccountMapper`, `AccountPersistenceAdapter`, `TokenIssuer`, `JwtTokenProvider`(정상+서명무효+만료+형식오류), `PasswordComplexityValidator`.
- `AuthControllerTest`(`@WebMvcTest`, Spring Security 체인 전체를 실제로 태워서 검증 — permitAll/authenticated 구분, 계정잠금 423 응답, 쿠키 헤더, 인증 성공/실패).
- `RefreshTokenJpaRepositoryIntegrationTest`(`@SpringBootTest` + Testcontainers) — `DELETE ... RETURNING`이 실제 Postgres에서 account_id를 반환하는지 검증. **이 샌드박스엔 Docker가 없어서 로컬에서 직접 실행은 못 해봤다** — `@Testcontainers(disabledWithoutDocker = true)`로 처리해 Docker 없는 환경에선 실패 대신 스킵되게만 해뒀다. Docker가 있는 환경(사용자 로컬, CI)에서 한 번은 꼭 실행해서 확인 필요.
- **JaCoCo 커버리지 90.8%** (`./gradlew :services:auth-service:jacocoTestCoverageVerification` 통과, 80% 기준 대비 여유 있음).

## 구현 중 발견한 버그 2건 (둘 다 수정 완료)

1. **`AuthServiceApplication`의 `main`이 `public`이 아니었음** — JVM/Spring Boot의 jar 런처는 `getMethod("main", ...)`로 리플렉션 조회하는데 이건 `public` 메서드만 찾는다. `public static void main`으로 수정.
2. **`JwtAuthenticationFilter`가 두 번 실행되는 버그** (진짜 프로덕션 버그, 테스트 중 발견) — `@Component`로 선언한 `Filter`는 Spring Security 체인에 `addFilterBefore`로 넣어도, Spring Boot가 **별개로 전역 서블릿 필터로도 자동 등록**해버린다. 그 결과 같은 필터가 두 번 불리면서 `OncePerRequestFilter` 가드 때문에 보안 체인 안쪽 실행이 조용히 스킵되고, 그 사이 `SecurityContextHolderFilter`가 인증 정보를 초기화해버려 **유효한 JWT를 넣어도 401이 나는 상태**였다. `SecurityConfig`에 `FilterRegistrationBean<JwtAuthenticationFilter>` + `setEnabled(false)`를 추가해 전역 자동등록을 끄는 방식으로 수정(표준적으로 알려진 해결법).

## 남은 것 (다음 단계에서 다룰 것)

- **member-service가 레포에 아직 없다** — `MemberServiceRestClient`는 `application-local.yml` 기준 `http://localhost:8082`로 호출하는데, member-service가 없으니 실제로 회원가입을 시도하면 항상 503(`DEPENDENCY_FAILURE`)이 나고 보상 트랜잭션(계정 삭제)이 실행된다. 이건 의도된 동작이지만, member-service가 붙기 전까진 AUTH-007을 엔드투엔드로 성공시켜볼 수 없다.
- **AUTH-002/008(소셜 로그인/가입), AUTH-009/010/014(이메일찾기·비밀번호재설정)는 구현 안 됨** — 원래 P1 이하였거나 범위에서 뺀 것들(AUTH-009는 2026-08-31 프로덕트 우선순위 조정으로 추가 후순위). `password_reset_tokens` 테이블은 이미 마이그레이션돼 있지만 이 테이블을 쓰는 코드는 없음. (AUTH-004/005는 2026-08-31 추가 세션에서 구현 완료 — 하단 참고.)
- **AUTH-012/013(배치)도 구현 안 됨** — P2, 이번 슬라이스 범위 아님.
- **Docker 없는 환경에서 실제 기동(Flyway 적용, `bootRun`)을 검증하지 못했다** — `docker compose up -d` 후 `./gradlew :services:auth-service:bootRun`으로 한 번 확인 필요(계획 문서의 검증 방법 2번). 특히 사용자가 V1에서 트리거를 제거한 게 실제로 Flyway를 통과하는지는 아직 직접 본 적 없다.
- **비밀번호 복잡도 규칙(최소 8자+3종류)과 토큰 수명(Access 30분/Refresh 14일)은 스펙에 구체 기준이 없어 가정한 값**이다 — 실제 정책이 다르면 `PasswordComplexityValidator`/`application-*.yml`의 `jwt.access-token-ttl`/`jwt.refresh-token-ttl`만 고치면 된다.
- `MemberServiceRestClient`, `AccountJpaEntity`(`@PrePersist`/`@PreUpdate`)는 Docker 기반 통합 테스트 없이는 완전히 검증되지 않는다 — 전체 커버리지는 80% 기준을 넉넉히 넘지만, 이 두 클래스 자체는 여전히 약하게 커버돼 있다.

## 다음 슬라이스 범위 조정 (우선순위 변경)

프로덕트 우선순위 조정으로 다음 항목이 후순위로 밀림(사용자 확정, 2026-08-31):
- 마이페이지 내 비밀번호 변경 / 내 정보 변경
- 고객센터
- 이메일찾기 (AUTH-009)
- 카테고리 목록 페이지

- "마이페이지 내 비밀번호 변경"은 auth-service 쪽 `PasswordChangeService`(AUTH-011)와는 별개로, 프론트 마이페이지 화면 자체의 우선순위 하향이다 — 백엔드 엔드포인트는 이미 Phase 4에서 구현 완료 상태이므로 재작업 대상 아님, 단순 참고용.
- 고객센터·카테고리 목록 페이지는 auth-service 소관이 아니라 다른 서비스(catalog/support 등) 영역으로 추정 — auth-service 다음 작업 계획엔 직접 영향 없음, 배경 정보로만 기록.
- 다음 auth-service 작업 슬라이스 계획 시 AUTH-009(이메일찾기)를 우선순위에서 낮춰 잡을 것.

## PortOne 통합인증(AUTH-005) 연동 (2026-08-31 추가 세션)

> 상세 계획은 `/Users/jaesung/.claude/plans/cozy-bouncing-stream.md` 참고. 벤더가 PortOne(KG이니시스, 카카오/네이버/PASS/토스/금융인증서 등 민간인증서) 통합인증으로 확정되면서 진행.

### 범위/설계 변경
- **CLAUDE.md 8행 정정**: "본인인증도 member-service 소관"이라는 구버전 문구를 "본인인증은 auth-service 자체 책임"으로 수정(문서 내 다른 곳·PRD.md와의 모순 해소).
- **AUTH-004(휴대폰 인증번호 발송) 폐기**: PortOne SDK가 클라이언트에서 인증창을 직접 열기 때문에 서버가 인증번호를 발송·관리할 역할이 없어짐. AUTH-005 하나로 통합.
- **AUTH-005 재정의**: `identityVerificationId`로 PortOne 결과를 서버가 조회·검증하고 `verificationToken`을 발급하는 형태로 변경. `AuthFunctionalSpec.md`/`AuthDomainApiSpec.md` 갱신 완료.
- **Redis를 이번에 처음 도입**: `verifiedCustomer`(이름/휴대폰번호/생년월일)를 회원가입 요청이 올 때까지 TTL 30분으로 임시 저장하는 용도. Redis vs RDBMS(`password_reset_tokens`와 동일 패턴) 두 옵션을 사용자와 논의했고, "일정 시간만 유효한 저장소"라는 요구사항에 TTL 자동만료가 자연스럽게 맞아떨어진다는 이유로 **Redis로 최종 확정**(사용자 확인, 2026-08-31). CI에는 별도 변경이 필요 없다고 판단함 — Postgres와 동일하게 Testcontainers가 테스트 실행 중 컨테이너를 직접 관리하는 방식(`config-convention.md` 원칙)을 그대로 적용했기 때문.

### Phase 7 — application/identity, infrastructure
- `application/identity/PortOneClient`(포트), `IdentityVerificationStore`(포트, `save`/`consume` — `consume`은 원자적 get-and-delete로 1회용 보장), `IdentityVerificationService`(유스케이스 — 미검증 시 `TOKEN_INVALID`, 검증 성공 시 `verificationToken` 발급).
- `infrastructure/portone/PortOneRestClient`(`MemberServiceRestClient`와 동일하게 connect/read 3초 타임아웃 + `DependencyFailureException` 래핑), `PortOneProperties`(`@ConfigurationProperties(prefix="portone")`, `JwtProperties`와 동일 패턴).
- `infrastructure/identity/RedisIdentityVerificationStore`(`StringRedisTemplate` + `tools.jackson.databind.ObjectMapper`로 JSON 직렬화 — Boot4/Jackson3 리브랜딩 패키지 주의).
- `AuthController`에 `POST /api/v1/auth/identity-verifications` 추가, `SecurityConfig` permitAll에 등록.
- `SignupService`: `SignupCommand`에 `verificationToken` 추가, 계정 생성 전 `identityVerificationStore.consume()`으로 1회 소비 후 `phoneNumber` 대조 — 기존 ponytail 코멘트(미검증 부채) 해소.
- 신규 에러코드 없음 — `CommonErrorCode.TOKEN_INVALID`/`DependencyFailureException` 재사용(`error-handling.md` 원칙).

### 인프라 변경
- `build.gradle`: `spring-boot-starter-data-redis` 추가. **Testcontainers에 공식 Redis 모듈이 없어**(Postgres와 달리) 별도 `testcontainers-redis` 아티팩트를 추가하지 않고, 이미 있는 `org.testcontainers:testcontainers` 코어의 `GenericContainer` + 이미지명 기반 `@ServiceConnection` 자동인식으로 처리했다.
- `docker-compose.yml`: `redis:7-alpine` 로컬 컨테이너 추가.
- `.env.example`: `PG_STORE_ID`/`PG_APIKEY` 플레이스홀더 추가.
- `application.yml`(공통): `portone.base-url`, `identity-verification.token-ttl` 추가 — 이 두 값은 비밀값이 아니라 모든 프로필에서 공유돼야 해서 공통 파일에 둠.
- `application-local.yml`/`application-dev.yml`: `spring.data.redis.host/port`, `portone.store-id`(`${PG_STORE_ID}`), `portone.api-secret`(`${PG_APIKEY}`) 추가. **`application-prod.yml`은 CLAUDE.md 규칙(배포 파이프라인 전용, 직접 수정 금지)에 따라 건드리지 않았다** — 같은 항목을 배포 파이프라인 쪽에서 추가해야 한다.

### 테스트
- `IdentityVerificationServiceTest`/`ExceptionTest`(Mockito).
- `PortOneRestClientTest` — `@RestClientTest` 대신 `MockRestServiceServer.bindTo(RestClient.Builder)`를 직접 사용하는 Spring-context-free 단위 테스트로 작성했다. 이유: `PortOneRestClient`(그리고 기존 `MemberServiceRestClient`)는 `RestClient.builder()`를 컴포넌트 내부에서 직접 호출해 만드는 패턴이라, Boot가 자동구성하는 `RestClient.Builder` 빈에 개입하는 `@RestClientTest`의 mock 서버 커스터마이저가 적용되지 않는다(직접 만든 커스텀 타임아웃 설정이 mock 설정을 덮어씀). 대신 `RestClient` 인스턴스를 직접 주입받는 패키지 프라이빗 생성자를 하나 추가해 테스트에서 mock으로 감싼 `RestClient`를 넣어주는 방식으로 우회했다. **`MemberServiceRestClient`도 같은 문제로 지금까지 테스트가 없었는데**, 이번에 만든 패턴을 그대로 적용하면 커버리지를 채울 수 있다(다음 슬라이스 후보).
- `RedisIdentityVerificationStoreIntegrationTest`(`@SpringBootTest` + Testcontainers `GenericContainer("redis:7-alpine")` + `@ServiceConnection`) — 1회 소비(get-and-delete) 원자성 검증. **이 샌드박스엔 Docker가 없어 스킵됨** — `RefreshTokenJpaRepositoryIntegrationTest`와 동일하게 Docker 있는 환경(로컬/CI)에서 한 번 실행 확인 필요.
- `SignupServiceTest`/`ExceptionTest`, `AuthControllerTest`에 verificationToken 정상/만료/불일치 케이스 및 `/identity-verifications` 엔드포인트 케이스 추가.
- 전체 테스트 통과, **JaCoCo 라인 커버리지 87%**(`jacocoTestCoverageVerification` 통과, 80% 기준). `infrastructure.identity`(0%)는 Docker 없는 샌드박스라 통합테스트가 스킵되어 그런 것 — `infrastructure.member`(기존 `MemberServiceRestClient`, 0%)와 같은 이유.

### 남은 것 / 확인 필요
- **`application-local.yml`이 git에 커밋돼 있지 않다**(`.gitignore` 대상) — CI(`ci-auth-service.yml`)가 체크아웃하는 트리에는 이 파일이 없는데, 공통 `application.yml`의 `spring.profiles.active: local`이 그대로 활성화된다. `member-service.base-url`처럼 로컬/dev/prod 프로필에만 있고 공통 파일엔 기본값이 없는 프로퍼티(`${...}` 플레이스홀더)가 있는 상태에서 `@SpringBootTest`가 그 빈을 생성하면 플레이스홀더 미해석으로 컨텍스트 로딩이 실패할 수 있다 — **이건 이번 세션이 만든 문제가 아니라 기존부터 있던 잠재 이슈**(`RefreshTokenJpaRepositoryIntegrationTest`도 동일 조건). 실제 CI(GitHub Actions)에서 `@SpringBootTest` 테스트가 정말 통과하는지 아직 확인된 적이 없어 보인다 — 확인 필요.
  - **2026-08-31 추가 세션에서 관련 버그를 하나 실제로 확인함**(아래 Phase 8 참고): `AuthControllerTest`의 `jwt.*` 프로퍼티는 `application-local.yml`이 로컬에 존재하면 오히려 테스트가 의도한 값을 **덮어써서 조용히 깨지는** 방향이었다 — 즉 이 특정 클래스에 한해서는 CI(파일 없음)가 로컬 개발 환경(파일 있음)보다 더 안전했다. `member-service.base-url`처럼 대체 소스가 전혀 없는 `@Value` 플레이스홀더는 여전히 반대 방향(CI에서만 실패) 위험이 남아있어 별개로 확인이 필요하다.
- **`application-prod.yml`에 이번 변경분(`portone.*`, `spring.data.redis.*`) 반영 필요** — 배포 파이프라인 쪽에서 별도 진행.
- PortOne 실제 스토어/채널 키가 없어 `/api/v1/auth/identity-verifications`를 실제 PortOne API와 엔드투엔드로 확인하지 못했다 — PortOne 콘솔에서 발급받은 테스트 키로 사용자가 로컬에서 직접 확인 필요.
- CI/DI는 이번 슬라이스에서 저장하지 않는다(가입 시 미사용) — 1인 1계정 중복가입 방지 등에 필요해지면 `IdentityVerificationStore.VerifiedIdentity`에 필드 추가.
- **`storeId` 쿼리 파라미터 추가(사용자 리뷰로 발견)**: 처음 구현에서는 `PG_STORE_ID`를 설정만 해두고 실제 PortOne 단건조회 호출에 쓰지 않고 있었다 — 사용자가 PortOne API 스펙의 `storeId` 쿼리 파라미터("접근 권한 있는 상점 아이디만 입력 가능")를 짚어줘서 `PortOneRestClient`에 반영(`?storeId={PG_STORE_ID}`), 다른 상점 소유의 인증 건이 조회되지 않도록 하는 방어를 추가했다.

## MVP 티켓 점검 + 로그인 Spring Security 필터 전환 (2026-08-31 추가 세션)

> 원래 MVP 티켓(회원가입·로그인·토큰 발급/검증) 수용 조건을 코드 기준으로 재점검하다가 실제 버그 하나(만료 토큰 처리)와 테스트 인프라 버그 하나(TestJwtConfig 무력화)를 발견해 같이 고쳤고, 사용자 요청으로 로그인을 Spring Security 인증 필터 체계로 전환했다.

### 버그 1 — 만료된 Access Token이 TOKEN_EXPIRED가 아니라 UNAUTHORIZED로 응답됨
- 원인: `JwtAuthenticationFilter.doFilterInternal()`이 `JwtTokenProvider.parseAccessToken()`이 던지는 `BusinessException(TOKEN_EXPIRED/TOKEN_INVALID)`를 `catch (RuntimeException e)`로 뭉뚱그려 잡아 그냥 버리고 있었다 — 그러면 `SecurityConfig`의 `AuthenticationEntryPoint`는 실패 사유를 몰라 항상 `CommonErrorCode.UNAUTHORIZED`로만 응답했다.
- 수정: `JwtAuthenticationFilter`가 `BusinessException`을 별도로 잡아 `request.setAttribute(AUTH_ERROR_ATTRIBUTE, e)`로 실어두고(그 외 `RuntimeException`은 기존처럼 그냥 무시), `SecurityConfig.onAuthenticationFailure()`가 그 attribute를 읽어 `TOKEN_EXPIRED`/`TOKEN_INVALID`/기본 `UNAUTHORIZED`를 구분해서 응답하도록 변경.
- `AuthControllerTest`에 만료 토큰(401 TOKEN_EXPIRED)/위조 서명 토큰(401 TOKEN_INVALID)/토큰 없음(401 UNAUTHORIZED) 3가지 케이스 추가.

### 버그 2 — `AuthControllerTest`의 `TestJwtConfig`가 조용히 무력화되고 있었음 (신규 테스트 작성 중 발견)
- 위 버그 1 테스트를 작성하는 과정에서, 직접 만든 만료 토큰이 실제로는 `TOKEN_INVALID`로 판정되는 이상 현상을 발견 → 원인 추적 결과, `AuthControllerTest`가 `@TestConfiguration`으로 `JwtProperties` 빈을 수동 생성해 시크릿을 고정하려 했지만, `JwtProperties`가 `@ConfigurationProperties`라서 Spring Boot의 `ConfigurationPropertiesBindingPostProcessor`가 **그 빈 인스턴스에 실제 프로필 파일(`application-local.yml`)의 값을 다시 바인딩해 덮어쓰고 있었다.** 로컬 개발 환경에만 `application-local.yml`이 존재하므로, 이 문제는 로컬에서만(또는 그 파일이 존재하는 CI 캐시 등에서만) 조용히 재현되고 CI에서는 오히려 의도한 값이 살아남는 역설적인 상황이었다.
- 수정: `@TestConfiguration`/수동 `@Bean JwtProperties` 방식을 버리고, `@TestPropertySource(properties = {"jwt.secret=...", ...})`로 최우선순위 프로퍼티를 직접 주입하는 방식으로 교체 — 환경에 관계없이 결정적으로 동작한다.

### 로그인 → Spring Security 인증 필터 체계 전환 (사용자 요청)
기존: `AuthController.login()`이 `LoginService.login()`(계정조회+잠금검사+비밀번호검증+실패기록+**토큰발급**)을 직접 호출.

- `LoginService`: 토큰 발급 책임을 떼어내고 `authenticate(String email, String password): Account`로 축소(자격증명 검증 전용). 도메인 예외(`AccountLockedException`, `BusinessException`)는 그대로 유지.
- 신규 `infrastructure/security/AccountAuthenticationProvider implements AuthenticationProvider` — `LoginService.authenticate()`를 호출하고 도메인 예외를 Spring Security 예외로 변환하는 얇은 어댑터(`AccountLockedException`→신규 `AccountLockedAuthenticationException extends LockedException`, 그 외 `BusinessException`→`BadCredentialsException`).
- 신규 `infrastructure/security/JsonLoginAuthenticationFilter extends UsernamePasswordAuthenticationFilter` — `POST /api/v1/auth/login`의 JSON 바디를 읽어 `AuthenticationManager`로 위임. 입력 검증은 null/blank 체크만 한다(사용자 확정) — 이메일 형식 오류 등은 계정 조회 실패로 자연스럽게 401 `INVALID_CREDENTIALS`로 이어지고, 기존 `@Valid` 기반 400은 이 경로에서 더 이상 나오지 않는다.
- 신규 `infrastructure/security/LoginSuccessHandler`/`LoginFailureHandler` — 성공 시 `TokenIssuer`+`RefreshTokenCookieFactory`로 토큰 발급·쿠키 조립 후 응답 바디 작성(기존 컨트롤러가 하던 일), 실패 시 계정잠금(423+lockedUntil)/자격증명불일치(401)를 `SecurityConfig.onAuthenticationFailure()`와 동일한 `ErrorResponse` 규격으로 응답.
- **레이어링 주의**: `presentation.dto.LoginRequest`/`LoginResponse`를 그대로 재사용하지 않고 `infrastructure/security` 안에 로컬 record(`LoginRequestBody`/`LoginResponseBody`)를 새로 뒀다 — `infrastructure`가 `presentation`을 참조하면 CLAUDE.md의 계층 방향(`presentation → application → domain → infrastructure`)이 거꾸로 되기 때문. 기존 `LoginRequest`/`LoginResponse` DTO는 더 이상 아무도 참조하지 않아 삭제했다.
- `SecurityConfig`: `AuthenticationManager` 빈(`new ProviderManager(accountAuthenticationProvider)`) 추가, `JsonLoginAuthenticationFilter`를 `addFilterAt(..., UsernamePasswordAuthenticationFilter.class)`로 등록(그 필터 슬롯을 완전히 대체), `authorizeHttpRequests` permitAll에서 `/login` 제거(필터가 `AuthorizationFilter` 이전에 요청을 끝내므로 더 이상 의미 없음).
- `AuthController.login()` 메서드 삭제, `GlobalExceptionHandler.handleAccountLocked(...)`도 삭제 — `AccountLockedException`이 이제 `AccountAuthenticationProvider`에서만 잡히고 컨트롤러 계층까지 올라오지 않아 사용처가 없어졌다(확인 완료, 삭제 전 grep으로 다른 사용처 없음 검증).
- 신규 테스트: `AccountAuthenticationProviderTest`/`ExceptionTest`, `LoginServiceTest`/`ExceptionTest`는 새 시그니처(`authenticate`)로 갱신, `AuthControllerTest`에 로그인 성공/자격증명불일치/계정잠금 3케이스를 `SecurityConfig` 전체 필터체인 기준으로 재작성.
- 전체 테스트 69개 통과, **JaCoCo 라인 커버리지 90%**(`jacocoTestCoverageVerification` 통과).

### 남은 것
- ~~`AuthDomainApiSpec.md` 219행 "잠금 중 로그인 시도는 401 + 잠금 해제 예정 시각 안내"는 실제 구현(423 `ACCOUNT_LOCKED`)과 다른 기존 문서 오기~~ **→ 사용자 확인 후 423로 정정 완료**.
- 이 슬라이스에서 발견한 회원가입 전체 흐름(계정 저장→member-service 호출→보상 트랜잭션/토큰 발급) 엔드투엔드 통합테스트 부재, 그리고 CI가 실제로 `@SpringBootTest` 부류를 통과시키는지 미확인 상태는 여전히 남아있다.

## test-convention.md 개정 반영 + 테스트 작성기준 점검 (2026-08-31 추가 세션)

### 단위 테스트 파일명 리네이밍
`test-convention.md` 개정으로 단위 테스트 접미사가 `XxxTest`/`XxxExceptionTest` → `XxxUnitTest`/`XxxUnitExceptionTest`로 바뀌었다(통합 테스트 `XxxIntegrationTest`/`XxxIntegrationExceptionTest`는 기존 그대로, 변경 없음). auth-service의 기존 단위 테스트 21개 파일을 `git mv` + `class` 선언 동시 수정으로 리네이밍. `AuthControllerTest`(`@WebMvcTest` — Spring 컨텍스트는 뜨지만 DB/Redis 등 실제 인프라 없이 서비스 계층 전부 Mock)는 단위/통합 어느 쪽에도 깔끔히 안 맞아 **사용자 확정으로 이번 리네이밍 대상에서 제외**, 기존 이름 유지. 리네이밍 전후 테스트 69개·통과 개수 동일 확인.

### "테스트 유형별 작성 기준" 표를 실제 코드에 대입해 점검
- **발견 1(수정 완료)**: `PortOneRestClientUnitTest`가 정상 케이스 2개 + 예외 케이스(`DependencyFailureException`) 1개를 한 파일에 섞어놓고 있었다 — "단위/예외 테스트는 파일로 분리한다" 규칙 위반(지난 세션에 만들면서 놓침). 예외 케이스를 `PortOneRestClientUnitExceptionTest`로 분리.
- **발견 2(기록만, 미수정 — 사용자 확정)**: "통합 예외 테스트" 작성 기준의 예시("DB 제약조건 위반")에 정확히 해당하는 미검증 지점을 하나 찾았다.
  - `accounts` 테이블엔 `uq_accounts_email` 유니크 제약(`V1__init_schema.sql:20`)이 있는데, `SignupService.signup()`은 `existsByEmail()` 확인 후 `save()`하는 **확인-후-실행(TOCTOU)** 패턴이다. 동시에 같은 이메일로 가입 요청 2개가 들어오면 하나는 `save()` 성공, 나머지 하나는 DB 유니크 제약 위반으로 `DataIntegrityViolationException`이 발생한다.
  - `AbstractGlobalExceptionHandler`(`modules:common-webmvc`)를 확인한 결과 이 예외를 **아무도 잡지 않는다** — `BusinessException`도 `DependencyFailureException`도 아니고 `ResponseEntityExceptionHandler`가 인식하는 Spring MVC 표준 예외 목록에도 없어서, 최후 방어선 `handleUnexpectedException`으로 떨어져 의도한 409(`EMAIL_ALREADY_EXISTS`)가 아니라 **500(`INTERNAL_ERROR`)으로 응답된다.**
  - 즉 이건 테스트 커버리지 공백일 뿐 아니라 **실제 동시 요청 상황에서 재현 가능한 잠재 버그**다(트래픽이 적으면 거의 안 보임). 고치려면: `AccountPersistenceAdapter.save()`(또는 `SignupService`)에서 `DataIntegrityViolationException`을 잡아 `BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS)`로 변환 + `SignupServiceIntegrationExceptionTest`(Testcontainers Postgres, 동시 저장 시도로 제약 위반 재현) 추가.
  - **이번 세션 범위에서는 수정하지 않기로 사용자 확정** — 다음 슬라이스 후보로 남겨둔다.
- 나머지는 기준에 부합함을 확인: 예외 없는 서비스(`EmailAvailabilityService`, `TokenIssuer`, `AccountMapper`)는 단위 예외 테스트 미작성이 맞고, 기존 통합 테스트 2개(`RefreshTokenJpaRepositoryIntegrationTest`, `RedisIdentityVerificationStoreIntegrationTest`)는 커스텀 쿼리/원자성처럼 "실제 인프라 배선 검증"이 필요한 지점이라 통합 테스트로 남기는 게 맞다. 동시성 테스트 대상(비관적 락/조건부 UPDATE/재고차감)은 auth-service에 아직 없다.

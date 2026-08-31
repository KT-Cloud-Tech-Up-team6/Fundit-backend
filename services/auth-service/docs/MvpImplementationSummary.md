# MVP 구현 요약 (AUTH-001, 003, 006, 007, 011)

> 이 문서는 이번 구현 세션에서 실제로 만든 것·바꾼 것·남은 것을 검토하기 위한 요약이다. 상세 계획은 `/Users/jaesung/.claude/plans/services-auth-service-docs-auth-function-imperative-whisper.md`(로컬 플랜 파일), 기능 요구사항은 `AuthFunctionalSpec.md`/`AuthDomainApiSpec.md` 참고.

## 범위 변경 (원래 계획 대비)

- **AUTH-004/005(휴대폰 인증번호 발송/검증)는 이번 슬라이스에서 제외**했다. Redis를 아직 안 쓰기로 했고, 프론트에서 별도 처리될 가능성이 있어 보류(작업 중 사용자 확정). `AuthController`에 해당 엔드포인트 없음.
- **AUTH-007(회원가입)의 `verificationToken` 필드는 요청 바디로 받되 검증하지 않는다**(위와 연동된 결정). 본인인증 없이 가입이 가능한 상태 — `SignupService`에 ponytail 주석으로 명시해뒀다. AUTH-004/005 붙이는 시점에 `verificationToken` → `phoneNumber` 대조 로직을 추가해야 한다.
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
- **AUTH-004/005(휴대폰 인증), AUTH-002/008(소셜 로그인/가입), AUTH-009/010/014(이메일찾기·비밀번호재설정)는 구현 안 됨** — 원래 P1 이하였거나 이번에 범위에서 뺀 것들. `password_reset_tokens` 테이블은 이미 마이그레이션돼 있지만 이 테이블을 쓰는 코드는 없음.
- **AUTH-012/013(배치)도 구현 안 됨** — P2, 이번 슬라이스 범위 아님.
- **Docker 없는 환경에서 실제 기동(Flyway 적용, `bootRun`)을 검증하지 못했다** — `docker compose up -d` 후 `./gradlew :services:auth-service:bootRun`으로 한 번 확인 필요(계획 문서의 검증 방법 2번). 특히 사용자가 V1에서 트리거를 제거한 게 실제로 Flyway를 통과하는지는 아직 직접 본 적 없다.
- **비밀번호 복잡도 규칙(최소 8자+3종류)과 토큰 수명(Access 30분/Refresh 14일)은 스펙에 구체 기준이 없어 가정한 값**이다 — 실제 정책이 다르면 `PasswordComplexityValidator`/`application-*.yml`의 `jwt.access-token-ttl`/`jwt.refresh-token-ttl`만 고치면 된다.
- `MemberServiceRestClient`, `AccountJpaEntity`(`@PrePersist`/`@PreUpdate`)는 Docker 기반 통합 테스트 없이는 완전히 검증되지 않는다 — 전체 커버리지는 80% 기준을 넉넉히 넘지만, 이 두 클래스 자체는 여전히 약하게 커버돼 있다.

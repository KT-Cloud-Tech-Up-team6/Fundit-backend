# MVP 구현 요약 (AUTH-001, 003, 005, 006, 007, 011)

> 이 문서는 구현 세션들에서 실제로 만든 것·바꾼 것·남은 것을 검토하기 위한 요약이다. 기능 요구사항은 `AuthFunctionalSpec.md`/`AuthDomainApiSpec.md` 참고.
>
> 구조: **"남은 것"** 절 하나에 미해결 항목을 전부 모아뒀다(카테고리별). 그 아래 **"세션별 구현 내역"**은 무엇을 왜 그렇게 만들었는지의 역사적 기록이며, 이미 해결된 항목은 거기서 "(해결됨)"으로만 표시하고 별도 TODO로 반복하지 않는다.

---

## 남은 것 (TODO, 2026-08-31 기준)

### 미구현 기능
- **AUTH-002/008(소셜 로그인/가입)** — 미구현, 범위 밖.
- **AUTH-009(이메일찾기)/010(비밀번호 재설정)/014** — 미구현. AUTH-009는 프로덕트 우선순위 조정으로 후순위 확정(사용자 확정, 2026-08-31). `password_reset_tokens` 테이블은 마이그레이션돼 있지만 사용하는 코드 없음.
- **AUTH-012/013(배치)** — 미구현, P2.

### 검증/실기동 필요
- **member-service가 레포에 아직 없다** — `MemberServiceRestClient`는 `http://localhost:8082`로 호출하는데 대상이 없어 회원가입 시도 시 항상 503(`DEPENDENCY_FAILURE`) + 보상 트랜잭션(계정 삭제)이 실행된다(의도된 동작). member-service가 붙기 전까진 AUTH-007을 엔드투엔드로 성공시켜볼 수 없다.
- **회원가입 전체 흐름(계정 저장 → member-service 호출 → 보상 트랜잭션/토큰 발급) 엔드투엔드 통합테스트가 없다** — 지금은 `SignupServiceTest`가 Mockito로 각 분기만 검증, 실제 Postgres 커밋/삭제까지 검증하는 Testcontainers 기반 테스트 부재.
- ~~Docker 없는 환경에서 실제 기동(Flyway 적용, `bootRun`) 미검증~~ **→ 2026-08-31 실제로 기동해서 확인함 — 그 과정에서 심각한 버그 3건을 발견·수정했다. 아래 "트러블슈팅 — 실기동 검증에서 발견한 버그 3건" 절 참고.**
- **CI(GitHub Actions)에서 `@SpringBootTest` 계열이 실제로 통과하는지 여전히 미확인** — `application-local.yml`이 `.gitignore` 대상이라 CI 체크아웃 트리엔 없는데, 공통 `application.yml`의 `spring.profiles.active: local`은 그대로 활성화된다. `member-service.base-url`처럼 로컬/dev/prod 프로필에만 있고 공통 파일엔 기본값이 없는 `@Value` 플레이스홀더가 있는 채로 컨텍스트가 뜨면 미해석으로 실패할 수 있다(기존부터 있던 잠재 이슈). 참고: `AuthControllerTest`에서 실제로 재현된 유사 버그는 방향이 반대였다 — local 파일이 있으면 오히려 테스트가 깨지는 쪽이었음(해결됨). 즉 이 항목은 케이스마다 위험 방향이 다를 수 있어, 실제 CI 실행 로그로 직접 확인 필요.
- **`application-prod.yml`에 이번 세션들의 변경분(`portone.*`, `spring.data.redis.*`) 반영 필요** — CLAUDE.md 규칙상 직접 수정 불가, 배포 파이프라인 쪽에서 진행.
- **PortOne 실제 스토어/채널 키로 엔드투엔드 미확인** — `/api/v1/auth/identity-verifications`를 실제 PortOne API로 검증한 적 없음. PortOne 콘솔에서 발급받은 테스트 키로 로컬에서 직접 확인 필요.

### 알려진 버그 / 개선 후보
- **Refresh Token 재사용 탐지(전체 세션 무효화) 시나리오를 고정하는 자동화 테스트가 없다** — 2026-08-31 실기동 트러블슈팅으로 버그를 찾아 고쳤지만(아래 "실기동 검증에서 발견한 버그 3건" 참고) 검증은 수동 `curl`로만 했다. `test-convention.md`의 "통합 예외 테스트" 기준이 예시로 드는 게 정확히 "트랜잭션 롤백" 상황이라, 로그인→재발급→재사용→(로테이션된 토큰도 같이 무효화되는지)까지 검증하는 `TokenRefreshServiceIntegrationExceptionTest`(Testcontainers)를 추가해 회귀를 막아야 한다. 다음 슬라이스 후보.
- **`SignupService`의 이메일 중복 체크가 TOCTOU라 동시 요청 시 500이 샐 수 있다** — `existsByEmail()` 확인 후 `save()`하는 구조라, 동시에 같은 이메일로 가입 요청이 오면 하나는 성공하고 나머지 하나는 `accounts.uq_accounts_email` 유니크 제약 위반으로 `DataIntegrityViolationException`이 발생한다. 이 예외를 잡는 핸들러가 없어 의도한 409(`EMAIL_ALREADY_EXISTS`)가 아니라 500(`INTERNAL_ERROR`)으로 응답된다(트래픽 적으면 거의 안 보임). 고치려면: `AccountPersistenceAdapter.save()`(또는 `SignupService`)에서 `DataIntegrityViolationException` → `BusinessException(EMAIL_ALREADY_EXISTS)` 변환 + Testcontainers 기반 통합 예외 테스트 추가. **사용자 확정으로 이번 세션 범위에서는 수정하지 않음** — 다음 슬라이스 후보.
- **`MemberServiceRestClient`, `AccountJpaEntity`(`@PrePersist`/`@PreUpdate`)는 여전히 약하게 커버됨** — Docker 기반 통합 테스트 없이는 완전히 검증 안 됨(전체 커버리지는 80% 기준을 넉넉히 넘음). `PortOneRestClient`에 적용한 `MockRestServiceServer` 패턴을 `MemberServiceRestClient`에도 그대로 적용하면 커버리지를 채울 수 있음(다음 슬라이스 후보).

### 정책값 확인 필요 (가정치로 구현됨)
- 비밀번호 복잡도 규칙(최소 8자+3종류 이상)과 토큰 수명(Access 30분/Refresh 14일)은 스펙에 구체 기준이 없어 가정한 값. 실제 정책이 다르면 `PasswordComplexityValidator`/`application-*.yml`의 `jwt.access-token-ttl`/`jwt.refresh-token-ttl`만 고치면 됨.
- CI(연계정보)/DI(중복가입확인정보)는 이번 슬라이스에서 저장하지 않음(가입 시 미사용) — 1인 1계정 중복가입 방지 등에 필요해지면 `IdentityVerificationStore.VerifiedIdentity`에 필드 추가.

---

## 세션별 구현 내역

### 범위 변경 (원래 계획 대비)
- **인증 방식은 커스텀 필터가 아니라 Spring Security(`spring-boot-starter-security`)**를 쓴다(작업 중 사용자 확정). Redis 의존성은 빼고, `spring-security-crypto` 대신 `spring-boot-starter-security` 전체를 사용.
- **AUTH-001 로그인 응답에서 `member.nickname`을 생략**한다(`AuthDomainApiSpec.md` 문서 스펙과 의도적으로 다름) — member-service가 아직 레포에 없어 로그인 경로에 불필요한 동기 결합을 만들지 않기 위함(사용자 확정). 응답은 `{accessToken, mustChangePassword}`만.
- **Refresh Token은 opaque 값이 아니라 JWT**다 — `AuthDomainApiSpec.md` AUTH-003 설명 중 "서명은 유효하지만 DB에 없는 토큰의 account_id 클레임 사용"이라는 문구를 근거로 확정. 클레임: `jti`=`refresh_tokens.token_id`, `sub`=`account_id`.
- **`V1__init_schema.sql`의 `set_updated_at()` 트리거 참조** 버그(정의 안 된 함수 참조) — 사용자가 직접 V1에서 트리거를 제거하고 `updated_at` 갱신을 JPA 엔티티(`@PreUpdate`)가 담당하는 방향으로 확정함(`persistence-convention.md` 예시와 동일 패턴).

### Phase 0~6 — 최초 MVP 스캐폴딩
- **Phase 0(설정)**: `build.gradle`(`modules:common-webmvc`, `spring-boot-starter-security`, `jjwt-{api,impl,jackson}:0.12.6`), `application.yml`/`application-local.yml`/`application-dev.yml`/`application-prod.yml`, `docker-compose.yml`+`.env.example`(Postgres만).
- **Phase 2(domain)**: `AuthErrorCode`(`INVALID_CREDENTIALS`/`ACCOUNT_LOCKED`/`EMAIL_ALREADY_EXISTS`), `Account`(복잡한 애그리거트 — 잠금/실패카운트 상태 전이), `Role`, `AccountRepository`(포트), `AccountLockedException`.
- **Phase 3(infrastructure)**: `persistence/account/*`(4파일 구조), `persistence/refreshtoken/*`(단순 애그리거트), `security/JwtTokenProvider`/`JwtProperties`/`JwtAuthenticationFilter`/`SecurityConfig`, `member/MemberServiceRestClient`(3초 타임아웃), `presentation/GlobalExceptionHandler`.
- **Phase 4(application)**: `LoginService`(AUTH-001), `TokenRefreshService`(AUTH-003, 회전+재사용탐지), `EmailAvailabilityService`(AUTH-006), `SignupService`(AUTH-007, 보상 트랜잭션 — `accountRepository.save()`가 독립 트랜잭션으로 즉시 커밋된 뒤 member-service 호출, 실패 시 `deleteById()`), `PasswordChangeService`(AUTH-011), `TokenIssuer`(로그인/회원가입/토큰재발급 공용).
- **Phase 5(presentation)**: `AuthController`, 요청/응답 DTO, `RefreshTokenCookieFactory`, `ValidPassword`/`PasswordComplexityValidator`.
- **Phase 6(테스트)**: 단위/예외 테스트 일습 + `AuthControllerTest`(`@WebMvcTest`) + `RefreshTokenJpaRepositoryIntegrationTest`(Testcontainers). JaCoCo 90.8%.
- **발견한 버그 2건(해결됨)**: ① `AuthServiceApplication.main`이 `public`이 아니어서 jar 런처가 못 찾던 문제 → `public static void main`으로 수정. ② `JwtAuthenticationFilter`(`@Component`)가 Spring Security 체인과 별개로 전역 서블릿 필터로도 자동 등록되어 두 번 실행되며 유효한 JWT도 401나던 문제 → `FilterRegistrationBean<JwtAuthenticationFilter>.setEnabled(false)`로 전역 자동등록 차단.

### 다음 슬라이스 우선순위 조정 (2026-08-31, 프로덕트 결정)
마이페이지 내 비밀번호/정보 변경, 고객센터, 이메일찾기(AUTH-009), 카테고리 목록 페이지가 후순위로 밀림(사용자 확정). "마이페이지 내 비밀번호 변경"은 백엔드(`PasswordChangeService`, AUTH-011)는 이미 구현 완료 상태라 재작업 대상 아니고 프론트 화면 우선순위 하향일 뿐. 고객센터·카테고리 목록은 auth-service 소관 아님(배경 정보로만 기록).

### PortOne 통합인증(AUTH-005) 연동
벤더가 PortOne(KG이니시스, 카카오/네이버/PASS/토스/금융인증서)으로 확정되면서 진행. 상세 계획은 `/Users/jaesung/.claude/plans/cozy-bouncing-stream.md` 참고.

- **AUTH-004(휴대폰 인증번호 발송) 폐기, AUTH-005 재정의**: PortOne SDK가 클라이언트에서 인증창을 직접 열기 때문에 서버의 "발송" 역할이 사라짐 — 서버는 `identityVerificationId`로 PortOne 결과를 조회·검증하고 `verificationToken`을 발급하는 역할만 함. `AuthFunctionalSpec.md`/`AuthDomainApiSpec.md` 갱신 완료.
- **CLAUDE.md 8행 정정**: "본인인증도 member-service 소관" → "본인인증은 auth-service 자체 책임"(문서 내 모순 해소).
- **Redis 최초 도입**: `verifiedCustomer`(이름/휴대폰번호/생년월일)를 회원가입 전까지 TTL 30분으로 임시 저장. Redis vs RDBMS(`password_reset_tokens` 패턴)를 사용자와 논의 후 "일정 시간만 유효한 저장소"라는 요구사항에 TTL 자동만료가 맞아떨어져 **Redis로 확정**. Testcontainers에 공식 Redis 모듈이 없어 `GenericContainer("redis:7-alpine")` + 이미지명 기반 `@ServiceConnection` 자동인식으로 처리(공식 `postgresql` 모듈과 다른 점).
- **구현**: `application/identity/{PortOneClient, IdentityVerificationStore, IdentityVerificationService}`, `infrastructure/portone/{PortOneRestClient, PortOneProperties}`(`MemberServiceRestClient`와 동일한 타임아웃/예외 패턴), `infrastructure/identity/RedisIdentityVerificationStore`(`StringRedisTemplate` + `tools.jackson.databind.ObjectMapper` — Boot4/Jackson3 리브랜딩 패키지 주의), `POST /api/v1/auth/identity-verifications` 엔드포인트. `SignupService`에 `verificationToken`→`phoneNumber` 대조 로직 추가(기존 ponytail 미검증 부채 해소). 신규 에러코드 없음(`CommonErrorCode.TOKEN_INVALID`/`DependencyFailureException` 재사용).
- **`storeId` 쿼리 파라미터 추가(해결됨, 사용자 리뷰로 발견)**: 처음엔 `PG_STORE_ID`를 설정만 해두고 실제 호출엔 안 쓰고 있었음 — PortOne API 스펙의 `storeId` 파라미터("접근 권한 있는 상점만 조회 가능")를 반영해 다른 상점 소유 인증 건이 조회되지 않도록 방어 추가.
- **인프라**: `spring-boot-starter-data-redis` 추가, `docker-compose.yml`에 `redis:7-alpine`, `.env.example`에 `PG_STORE_ID`/`PG_APIKEY`, `application.yml`(공통)에 `portone.base-url`/`identity-verification.token-ttl`, local/dev에 `spring.data.redis.*`+`portone.store-id`/`api-secret`(`application-prod.yml`은 위 "남은 것" 참고).
- **테스트**: `IdentityVerificationServiceTest`/`ExceptionTest`, `RedisIdentityVerificationStoreIntegrationTest`, `PortOneRestClientTest`(`@RestClientTest` 대신 `MockRestServiceServer.bindTo(RestClient.Builder)` 직접 사용 — `PortOneRestClient`가 `RestClient.builder()`를 컴포넌트 내부에서 직접 호출하는 패턴이라 Boot 자동구성 `RestClient.Builder` 빈에 개입하는 `@RestClientTest`의 mock 커스터마이저가 안 먹힘. `RestClient`를 직접 주입받는 패키지 프라이빗 생성자로 우회). JaCoCo 87%.

### MVP 티켓 점검 + 로그인 Spring Security 필터 전환
원래 MVP 티켓(회원가입·로그인·토큰 발급/검증) 수용 조건을 코드 기준으로 재점검하며 발견.

- **버그(해결됨) — 만료 Access Token이 TOKEN_EXPIRED 아닌 UNAUTHORIZED로 응답**: `JwtAuthenticationFilter`가 `JwtTokenProvider.parseAccessToken()`의 `BusinessException(TOKEN_EXPIRED/TOKEN_INVALID)`를 `catch (RuntimeException e)`로 뭉뚱그려 버리던 게 원인. `BusinessException`을 별도로 잡아 `request.setAttribute(...)`에 실어두고 `SecurityConfig.onAuthenticationFailure()`가 그걸 읽어 구분 응답하도록 수정. `AuthControllerTest`에 만료/위조서명/토큰없음 3케이스 추가.
- **버그(해결됨) — `AuthControllerTest`의 `TestJwtConfig`가 조용히 무력화되던 문제(위 버그 테스트 작성 중 발견)**: `JwtProperties`가 `@ConfigurationProperties`라서, `@TestConfiguration`으로 만든 빈도 `ConfigurationPropertiesBindingPostProcessor`가 실제 프로필 파일(`application-local.yml`) 값으로 재바인딩해 덮어썼다 — 로컬에 그 파일이 있는 개발 환경에서만 조용히 재현되는 역설적 상황. `@TestPropertySource(properties = {...})`로 교체해 환경과 무관하게 결정적으로 동작하도록 수정.
- **로그인 → Spring Security 인증 필터 체계 전환 (사용자 요청)**: `LoginService`는 자격증명 검증만 담당하도록 축소(`authenticate(email, password): Account`, 토큰 발급 책임 제거). 신규 `AccountAuthenticationProvider`(도메인 예외↔Spring Security 예외 변환 어댑터), `JsonLoginAuthenticationFilter`(JSON 바디 로그인, 입력검증은 null/blank만 — 이메일 형식오류 등은 401로 자연 처리), `LoginSuccessHandler`/`LoginFailureHandler`(토큰발급+쿠키/에러응답 조립). `SecurityConfig`에 `AuthenticationManager` 빈 추가, 로그인 필터를 `UsernamePasswordAuthenticationFilter` 슬롯에 `addFilterAt`으로 등록. `AuthController.login()`, `GlobalExceptionHandler.handleAccountLocked(...)`, 안 쓰게 된 `LoginRequest`/`LoginResponse` DTO 삭제(레이어링 위반 방지 위해 `infrastructure/security` 안에 로컬 record로 대체). 전체 테스트 69개 통과, JaCoCo 90%.
- `AuthDomainApiSpec.md` 219행 "잠금 시 401" 오기 → 423(`ACCOUNT_LOCKED`)로 정정(해결됨).

### test-convention.md 개정 반영 + 테스트 작성기준 점검
- **단위 테스트 파일명 리네이밍(해결됨)**: 접미사 `XxxTest`/`XxxExceptionTest` → `XxxUnitTest`/`XxxUnitExceptionTest`(통합 테스트는 변경 없음). 21개 파일을 `git mv`+`class` 선언 동시 수정. `AuthControllerTest`(`@WebMvcTest`)는 단위/통합 어느 쪽에도 안 맞아 사용자 확정으로 리네이밍 대상에서 제외. 전후 테스트 69개·통과 개수 동일 확인.
- **"테스트 유형별 작성 기준" 표를 실제 코드에 대입한 점검**: `PortOneRestClientUnitTest`가 정상/예외 케이스를 한 파일에 섞어놓은 규칙 위반 발견 → `PortOneRestClientUnitExceptionTest`로 분리(해결됨). "통합 예외 테스트" 기준(DB 제약조건 위반)에 해당하는 미검증 지점(이메일 중복 체크 TOCTOU)도 발견 — 위 "남은 것 > 알려진 버그" 참고. 나머지(예외 없는 서비스의 예외테스트 미작성, 기존 통합테스트 2개, 동시성테스트 대상 없음)는 기준에 부합함을 확인.

### 트러블슈팅 — 실기동 검증에서 발견한 버그 3건 (2026-08-31, 전부 해결됨)

`docker compose up -d` + `./gradlew :services:auth-service:bootRun`으로 실제 기동을 처음 해봤다(그동안 샌드박스에 Docker가 없어 못 해봤던 검증 — Docker Desktop을 이번에 직접 띄우고 `.env`(사용자가 이미 만들어둔 실제 PortOne 키 포함)를 로드해 실행). 이전까지 "단위/슬라이스 테스트 전부 통과·JaCoCo 90%"였음에도 **앱이 두 차례 연속 부팅 자체에 실패했고, 세 번째로 뜬 뒤에는 핵심 보안 기능이 조용히 무력화돼 있었다.** 아래는 각각 증상/원인/해결/더 나은 방안을 정리한 것.

#### 버그 1 — 부팅 실패: `SecurityConfig`↔`AccountAuthenticationProvider`↔`LoginService` 순환 빈 참조
- **증상**: `bootRun` 시 `UnsatisfiedDependencyException: ... Requested bean is currently in creation: Is there an unresolvable circular reference?`로 기동 자체가 실패.
- **원인**: `SecurityConfig`가 (로그인 필터 전환 세션에서 새로 추가된) `AccountAuthenticationProvider`를 의존하는데, `AccountAuthenticationProvider`→`LoginService`→`PasswordEncoder`로 이어지고, 그 `PasswordEncoder` 빈이 다름 아닌 `SecurityConfig` 자기 자신 안에 `@Bean`으로 정의돼 있어 완전한 순환 고리가 만들어졌다. `@WebMvcTest(AuthControllerTest)`는 필요한 빈을 전부 `@Import`로 수동 등록하는 방식이라 이 순환을 아예 재현하지 않았고, 그래서 테스트로는 잡을 수 없었다.
- **해결**: `PasswordEncoder` 빈을 `SecurityConfig`에서 떼어내 별도의 `PasswordEncoderConfig` 클래스로 분리.
- **더 나은 방안(적용은 안 함)**: `@Lazy`로 순환을 우회하거나 `spring.main.allow-circular-references=true`로 넘어가는 방법도 있지만, 둘 다 근본 설계 문제(보안설정 클래스가 로그인 도메인 로직까지 의존하게 된 구조)를 감추기만 할 뿐이라 채택하지 않았다. 지금 적용한 "관심사 분리(빈 소유 클래스 분리)"가 정공법에 가깝다. 다만 이런 순환은 `@WebMvcTest`류 슬라이스 테스트로는 원천적으로 못 잡으므로, 서비스에 전체 컨텍스트를 실제로 띄워보는 `@SpringBootTest`(Mock 없이) 스모크 테스트 하나를 CI에 추가해두면 다음에 비슷한 순환이 생겨도 PR 단계에서 바로 잡을 수 있다 — 지금은 그런 테스트가 없다(아래 "남은 것" 참고).

#### 버그 2 — 부팅 실패: `PortOneRestClient` 생성자 모호성
- **증상**: 버그 1을 고친 뒤 다시 `bootRun` → `BeanInstantiationException: ... NoSuchMethodException: PortOneRestClient.<init>()`(기본 생성자를 찾다 실패).
- **원인**: `PortOneRestClient`에 생성자가 2개 있었다 — 운영용 `PortOneRestClient(PortOneProperties)`와 테스트 전용 `PortOneRestClient(RestClient, String, String)`(지난 세션에 `MockRestServiceServer` 테스트를 위해 추가한 것). 어느 쪽에도 `@Autowired`가 없어 Spring이 둘 중 뭘 써야 할지 못 정하고 기본 생성자를 찾다 실패했다. `PortOneRestClientUnitTest`는 테스트용 생성자를 `new`로 직접 호출해 DI 자체를 거치지 않으므로 이 문제를 볼 수 없었고, 이 클래스를 실제 빈으로 띄우는 유일한 테스트(`RedisIdentityVerificationStoreIntegrationTest`, `@SpringBootTest`)는 Docker가 없어 계속 스킵되고 있어 여태 드러나지 않았다.
- **해결**: 운영용 생성자에 `@Autowired`를 명시.
- **더 나은 방안(적용은 안 함)**: 더 근본적인 해법은 애초에 "테스트 전용 생성자"라는 구멍을 만들지 않는 것이다 — `PortOneRestClient`가 `RestClient`를 스스로 만들지 않고, 별도 `@Configuration`에서 `RestClient` 빈 자체를 만들어 주입받게 하면(`MemberServiceRestClient`도 같은 패턴) 생성자가 하나뿐이라 이런 모호성 자체가 생기지 않고, 테스트도 그 `RestClient` 빈만 교체하면 되어 별도 테스트용 생성자가 필요 없어진다. 이번엔 기존 코드 스타일(`MemberServiceRestClient`와 동일한 "자기 안에서 RestClient 생성" 패턴)을 유지하려고 최소 diff로 `@Autowired`만 추가했는데, 다음에 이 클래스들을 다시 손볼 일이 생기면 이 구조로 리팩터링하는 걸 고려할 만하다.

#### 버그 3 — 부팅은 되지만 Refresh Token 재사용 탐지(전체 세션 강제 로그아웃)가 무력화됨 — 가장 심각한 발견
- **증상 (3-1)**: `POST /api/v1/auth/token/refresh`를 호출할 때마다 500. 로그: `InvalidDataAccessApiUsageException: Modifying queries can only use void, int/Integer, or long/Long as return type` (`deleteAndReturnAccountId`) 와 `No active transaction for update or delete query`(`deleteAllByAccountId`).
- **원인 (3-1)**: `RefreshTokenJpaRepository.deleteAndReturnAccountId()`에 `@Modifying`이 붙어 있었는데, 바로 위 주석은 "DELETE...RETURNING 결과를 읽으려면 일부러 @Modifying을 붙이면 안 된다"고 설명하고 있었다 — **주석이 말하는 설계와 실제 코드가 어긋나 있던 상태**(이전 세션 어느 시점에 애노테이션이 실수로 남거나 다시 붙은 것으로 보임). `@Modifying`이 있으면 Spring Data JPA가 리턴 타입으로 `void`/`int`/`long`만 허용해 `Optional<UUID>`를 거부한다. `deleteAllByAccountId()`는 커스텀 `@Query` 메서드라 `SimpleJpaRepository`의 기본 CRUD 메서드와 달리 자동으로 트랜잭션이 걸리지 않는데, 호출부인 `TokenRefreshService.refresh()`에는 애초부터 `@Transactional`이 없었다.
- **1차 해결**: `deleteAndReturnAccountId`에서 `@Modifying` 제거(주석이 원래 의도했던 상태로 되돌림), `TokenRefreshService.refresh()` 전체를 `@Transactional`로 감쌈.
- **증상 (3-2, 1차 해결 후 재현)**: `curl`로 로그인 → 재발급(로테이션) → **로테이션 전 옛 토큰을 재사용**(탈취 시나리오 재현)까지 직접 실행해보니, 옛 토큰 재사용은 401로 잘 막혔다. 그런데 **그 직전에 정상적으로 발급된(로테이션된) 새 토큰으로 다시 호출하면 여전히 200이 나왔다** — CLAUDE.md가 명시한 "재사용 탐지 시 해당 계정 전체 세션 강제 로그아웃"이 실제로는 전혀 동작하지 않고 있었다.
- **원인 (3-2)**: `refresh()` 전체를 `@Transactional`로 감싼 상태에서 재사용이 감지되면 `deleteAllByAccountId()`(전체 세션 삭제)를 실행한 **바로 다음 줄에서** `BusinessException`을 던지는데, Spring은 기본적으로 처리되지 않은 런타임 예외가 트랜잭션 메서드 밖으로 던져지면 그 트랜잭션 전체를 롤백한다 — 그래서 방금 실행한 전체 세션 삭제까지 같이 취소됐다. "공격자를 막는 것"과 "그 사실을 호출자에게 401로 알리는 것"이 같은 트랜잭션 안에 있으면 안 되는 경우였다.
- **2차 해결**: 서비스 메서드의 `@Transactional`을 제거하고, `deleteAllByAccountId` 리포지토리 메서드 자체에 `@Transactional`을 달았다 — 이 삭제만은 호출부에서 이후에 어떤 예외가 나든 상관없이 독립적으로 즉시 커밋된다.
- **더 나은 방안(고려했으나 채택 안 함)**: `@Transactional(noRollbackFor = BusinessException.class)`로 서비스 메서드 전체를 계속 감싸는 방법도 있었다 — "이 흐름 전체가 하나의 트랜잭션"이라는 의도가 더 명시적으로 드러나긴 한다. 다만 이 방식은 **예상한 `BusinessException`에 대해서만** 롤백을 막을 뿐이라, 나중에 이 메서드에 버그가 생겨 **다른** 런타임 예외가 새로 던져지면 그때는 다시 세션 삭제가 롤백돼버리는 동일한 함정에 빠질 수 있다. 지금 적용한 "보안에 중요한 삭제 자체를 독립 트랜잭션으로 만드는" 방식이 어떤 예외가 나든 항상 안전하다는 점에서 더 견고하다고 판단해 이쪽을 최종 선택했다.
- **테스트 공백(별도로 남겨둠)**: 이 버그는 `curl`로 직접 재현해서 찾고 고쳤을 뿐, 자동화된 테스트로 고정하지 않았다 — `test-convention.md`의 "통합 예외 테스트" 작성 기준이 예시로 드는 것이 정확히 "트랜잭션 롤백"이라, 지금 이 시나리오(로그인→재발급→재사용→로테이션된 토큰도 같이 무효화되는지)를 검증하는 `TokenRefreshServiceIntegrationExceptionTest`(Testcontainers)가 없다는 게 남은 공백이다. 아래 "남은 것"에 추가함.

**부수 발견**: `RedisIdentityVerificationStoreIntegrationTest`도 이번에 처음 Docker로 돌려보니 컨텍스트 로딩에 실패했다 — `@SpringBootTest`라 Redis뿐 아니라 JPA/DataSource가 필요한 전체 앱 컨텍스트가 뜨는데 Postgres Testcontainer를 안 띄워서 `application-local.yml`의 하드코딩된 `localhost:5432`로 접속하다 연결 거부로 실패했다. `RefreshTokenJpaRepositoryIntegrationTest`와 동일하게 `PostgreSQLContainer`를 같이 등록해서 해결.

**최종 재검증**: 위 수정을 전부 반영한 뒤 `curl`로 로그인 → 재발급(로테이션 성공, HTTP 200) → 옛 토큰 재사용(401 확인) → 방금까지 유효했던 로테이션된 토큰 재시도(**401로 정상 차단 — 전체 세션 무효화가 실제로 동작함을 확인**)까지 전 구간을 직접 확인했다. `./gradlew test`도 69개 전부 통과(Docker가 떠 있어 두 통합테스트도 스킵 없이 실제로 실행됨, JaCoCo 기준 통과). 스모크 테스트에 쓴 계정(`smoketest@fundit.com`, member-service가 없어 signup 대신 SQL로 직접 INSERT)과 `docker compose` 리소스는 검증 후 전부 정리함.

**교훈**: 세 버그 전부 "단위/슬라이스 테스트는 전부 통과, 커버리지도 기준 이상"인 상태에서 실기동으로만 드러났다. 특히 버그 3은 이 서비스의 핵심 보안 요구사항이 실제로는 지켜지지 않고 있었던 경우다. 근본 원인은 두 가지: ① `@WebMvcTest`/`@InjectMocks` 기반 테스트는 실제 빈 그래프 순환이나 생성자 주입 모호성을 재현하지 못한다(전체 컨텍스트를 실제로 띄우는 테스트가 없으면 못 잡음), ② 트랜잭션 경계와 예외 처리의 상호작용(롤백이 의도한 부수효과를 지워버리는 것)은 Mockito 기반 단위 테스트로는 원천적으로 검증 불가능하고 실제 트랜잭션 매니저가 동작하는 통합 테스트가 있어야 잡힌다. 전체 컨텍스트 기동 + 실제 공격 시나리오 재현 같은 검증을 정기적으로 반복할 가치가 있다.

# gateway-service 구현 요약 (Phase 2, `feat/gateway-service#21`)

> 이 문서는 게이트웨이에서 **무엇을 만들었고, 왜 그렇게 설계했으며, 어떤 공격을 막고 어떤 건 아직 못 막는지**를 정리한다.
> API 계약은 각 서비스의 `docs/*ApiSpec.md`를, 레포 공통 규칙은 루트 `CLAUDE.md`와 `.claude/rules/`를 참고.

---

## 1. 왜 만들었나 — 이전 상태의 구멍 두 개

Phase 1까지 auth-service와 member-service는 붙어 동작했지만, 인증 경계에 두 가지가 뚫려 있었다.

**① `X-Account-Id` 위조.** member-service의 `CurrentMemberArgumentResolver`는 헤더에 담긴 계정 ID를 **서명 검증 없이 그대로** 신뢰했다. 코드에도 부채로 명시돼 있었다:

> `ponytail: 게이트웨이가 아직 없어 서명 검증 없이 X-Account-Id 헤더를 그대로 신뢰한다 (내부망 전제 하의 임시 조치)`

즉 `curl -H "X-Account-Id: <남의 UUID>" .../members/me` 한 줄이면 아무나 다른 회원의 프로필·배송지·찜을 조회할 수 있었다. "내부망 전제"라고 적혀 있었지만 그 전제가 실제 배포 환경에서 성립하는지는 확인된 바 없었다.

**② 내부 전용 엔드포인트가 반쪽만 막혀 있었다.** `POST /api/v1/members`(auth-service가 회원가입 중 호출하는 프로필 생성)는 `X-Internal-Api-Key`로 막혀 있었지만, 설계상 짝을 이뤄야 할 "게이트웨이 라우팅에서 제외"(네트워크 격리)가 없었다.

---

## 2. 전체 흐름

```
[클라이언트]
    │  Authorization: Bearer <accessToken>
    │  (X-User-Id 같은 걸 끼워넣어도 게이트웨이가 버린다)
    ▼
[gateway-service :8080]  JwtHeaderGlobalFilter
    │  1. 클라이언트가 보낸 X-User-Id / X-User-Roles / X-Internal-Api-Key 제거
    │  2. POST /api/v1/members, /api/v1/members/social 이면 404 (내부 전용)
    │  3. X-Internal-Api-Key 주입 (게이트웨이를 거쳤다는 증명)
    │  4. 토큰 없으면 → 사용자 헤더 없이 통과 (인증 필요 여부는 다운스트림이 판단)
    │  5. 토큰 있으면 → HS256 서명 검증 → sub → X-User-Id, role → X-User-Roles
    │     무효/만료 → 여기서 401 (TOKEN_INVALID / TOKEN_EXPIRED)
    ▼
[member-service :8082]  InternalGatewaySecretFilter (modules:common-webmvc)
    │  X-User-Id가 있거나 내부 전용 경로면 → X-Internal-Api-Key 검증, 틀리면 401
    ▼
                        LoginUserArgumentResolver
    │  X-User-Id / X-User-Roles → CurrentUser 로 조립
    ▼
[컨트롤러]  getMe(@LoginUser CurrentUser user) { ... user.id() ... }
```

`Authorization` 헤더는 **제거하지 않고 그대로 전달**한다. auth-service의 `PATCH /api/v1/auth/password`는 토큰 발급자 본인으로서 자체 Spring Security 필터로 한 번 더 검증하기 때문이다.

---

## 3. 만든 것

### `platform:gateway-service` (신규)

| 파일 | 역할 |
|---|---|
| `GatewayServiceApplication` | 부트 진입점 |
| `config/JwtDecoderConfig` | `ReactiveJwtDecoder` 빈 — HS256 대칭키 검증기 |
| `filter/JwtHeaderGlobalFilter` | 위 흐름의 1~5 전부. 게이트웨이의 실질적 전부다 |
| `application*.yml` (4개) | 포트 8080, 라우팅, 다운스트림 주소, 시크릿 |

라우팅:

| 경로 | 대상 |
|---|---|
| `/api/v1/auth/**` | auth-service (8081) |
| `/api/v1/members/**`, `/api/v1/wishes/**`, `/api/v1/addresses/**`, `/api/v1/terms/**` | member-service (8082) |

### `modules:common-webmvc` (인증 플러밍 추가)

| 클래스 | 역할 |
|---|---|
| `@LoginUser` | 컨트롤러 파라미터 마커 |
| `CurrentUser(UUID id, List<String> roles)` | 인증 정보 DTO |
| `LoginUserArgumentResolver` | 헤더 → `CurrentUser` 조립 |
| `InternalGatewaySecretFilter` | 게이트웨이 경유 여부 검증 |
| `InternalEndpoint` | 내부 전용 엔드포인트 선언(메서드+경로) |
| `CommonWebConfig` | 위 리졸버·필터 등록 |

### `modules:common`

`AuthHeaders` — 헤더 이름 상수(`X-User-Id`/`X-User-Roles`/`X-Internal-Api-Key`).

### member-service (마이그레이션)

`CurrentMember`, `CurrentMemberArgumentResolver`, `InternalApiKeyFilter`, `WebConfig` **삭제** → 공통 모듈 것으로 대체. 컨트롤러 3개가 `@LoginUser CurrentUser`를 받도록 변경. `InternalEndpointConfig`로 내부 전용 경로 선언. 메인 클래스에 `scanBasePackages = "com.fundit"` 추가.

---

## 4. 설계 판단과 근거

### 4-1. JWT 서명: RS256이 아니라 HS256 유지 (한시적)

레퍼런스 아키텍처는 RS256 + JWKS였다. 이번엔 **HS256 대칭키 공유**로 가고, RS256 전환은 별도 이슈로 뺐다(사용자 확정).

- **이유**: RS256으로 가려면 auth-service의 `JwtTokenProvider` 재작성 + 키페어 생성·보관 + JWKS 엔드포인트 신설 + 운영 키 주입 체계까지 따라온다. 게이트웨이의 본래 목적(헤더 위조 차단)과는 독립된 작업량이라 한 이슈에 묶으면 둘 다 늦어진다.
- **감수한 단점**: `jwt.secret`이 auth-service와 게이트웨이 **두 곳**에 존재한다. 대칭키라 게이트웨이가 유출되면 토큰 위조까지 가능하다(RS256이면 공개키만 있어 검증만 가능).
- **완화**: 검증기를 `JwtDecoderConfig` 한 클래스로 격리했다. RS256 전환은 `withSecretKey(...)` → `withJwkSetUri(...)` 한 줄 교체로 끝나고 필터는 손대지 않는다.

### 4-2. 검증 라이브러리: jjwt가 아니라 Nimbus

auth-service는 jjwt를 쓰지만 게이트웨이는 `spring-security-oauth2-jose`의 `NimbusReactiveJwtDecoder`를 쓴다. RS256 전환 시 `withJwkSetUri()`가 JWKS 캐싱·키 로테이션을 알아서 처리해주기 때문이다. 지금 jjwt로 맞춰두면 그때 다시 갈아엎어야 한다.

`spring-boot-starter-oauth2-resource-server`(전체 스타터)가 아니라 `spring-security-oauth2-jose`(JOSE 라이브러리)만 가져온 이유: 전자는 `spring-boot-starter-security`를 끌고 와 원치 않는 리액티브 시큐리티 필터체인을 자동 구성한다. 인증 판단은 우리 필터가 직접 하므로 그 체인이 끼어들 자리가 없다.

### 4-3. 내부 시크릿: 새 헤더를 만들지 않고 기존 `X-Internal-Api-Key` 재사용

레퍼런스는 `X-Internal-Secret`을 새로 도입했지만, Phase 1에서 이미 같은 목적의 `X-Internal-Api-Key`가 auth→member 경로에 들어가 있었다. 두 개를 병행하면 관리 포인트만 늘고 "어느 쪽을 언제 쓰는지"가 헷갈린다. **하나로 통일**했다.

### 4-4. 내부 전용 경로: yml 설정이 아니라 빈으로 선언

처음엔 `internal-api.internal-endpoints`를 yml에 두려 했으나 **빈 선언**(`InternalEndpointConfig`)으로 바꿨다.

- 내부 전용 여부는 **환경마다 달라지는 값이 아니라 그 서비스 API의 성질**이다.
- 설정으로 두면 특정 프로필(특히 `application-prod.yml`)에서 빠뜨렸을 때 **조용히 무방비**가 된다. 게다가 `application-prod.yml`은 이 레포에서 직접 수정 금지 대상이라, 설정 방식은 운영 환경에서 누락되기 가장 쉬운 형태였다.

### 4-5. 헤더 이름 상수를 `modules:common`에 둔 이유

게이트웨이(WebFlux)와 서비스(Servlet)는 스택이 달라 코드를 공유할 수 없지만, 헤더 이름은 반드시 일치해야 한다. 양쪽이 각자 문자열 리터럴을 들고 있으면 한쪽만 바뀌어도 조용히 인증이 깨진다.

이건 가정이 아니라 **이 레포에서 실제로 일어난 사고**다 — Phase 1에서 member-service는 `X-Internal-Api-Key`를 요구하는데 auth-service가 보내지 않아 회원가입이 100% 실패했고, 두 서비스 모두 테스트는 초록불이었다. 그래서 `AuthHeaders` 상수를 공유한다.

### 4-6. 토큰이 없으면 게이트웨이가 막지 않는다

게이트웨이는 "인증이 필요한 경로 목록"을 갖지 않는다. 토큰이 없으면 사용자 헤더 없이 그냥 통과시키고, 401 판단은 다운스트림에 맡긴다.

목록을 게이트웨이에도 두면 서비스의 실제 인증 요구사항과 **두 벌**이 되고, 언젠가 반드시 어긋난다(게이트웨이는 공개로 알고 있는데 서비스는 인증 필요로 바뀐 경우 등). 단, 토큰이 **있는데 무효/만료**면 그건 게이트웨이가 확실히 아는 사실이므로 여기서 401로 끊는다 — 그대로 흘려보내면 다운스트림이 원인 구분 없이 `UNAUTHORIZED`만 내보낸다.

---

## 5. 보안 분석

### 5-1. 막게 된 공격

| 공격 | 이전 | 지금 |
|---|---|---|
| 게이트웨이 경유로 `X-User-Id` 위조 | — | 게이트웨이가 클라이언트 헤더를 **무조건 제거** |
| 유효한 본인 토큰 + 남의 `X-User-Id` 동시 전송 | — | 토큰의 `sub` 값으로 **덮어씀** |
| 서비스 포트 직접 호출 + 헤더 위조 | **뚫림** | `X-Internal-Api-Key` 없어 401 |
| 내부 전용 `POST /api/v1/members(/social)` 외부 호출 | **뚫림** | 게이트웨이 404 + 서비스 필터 401 |
| refresh 토큰으로 일반 API 호출 | — | `typ` 클레임 확인 후 401 |
| 위조 서명 / 만료 토큰 | — | 게이트웨이가 401 (`TOKEN_INVALID`/`TOKEN_EXPIRED`) |

### 5-2. ⚠️ 설계 중 발견한 함정 — 내부 키 주입이 만든 새 구멍

기존 키를 재사용하기로 하면서 **게이트웨이가 모든 프록시 요청에 `X-Internal-Api-Key`를 주입**하게 됐다. 그런데 이러면:

> 외부 클라이언트가 게이트웨이를 통해 `POST /api/v1/members`를 호출 → 게이트웨이가 친절하게 내부 키를 붙여줌 → member-service의 내부 키 검증을 **그냥 통과**

즉 게이트웨이를 세우는 것만으로 **이전보다 오히려 취약해질 수 있었다.** 그래서 게이트웨이가 이 경로+메서드를 404로 끊는 것이 선택이 아니라 **필수**다. 두 방어선의 역할이 다르다:

- 게이트웨이 404 → **외부에서의 접근**을 막는다
- `InternalGatewaySecretFilter` → **게이트웨이를 우회한 직접 호출**을 막는다

어느 한쪽만으로는 반대쪽이 뚫린다. `MemberController`와 `InternalEndpointConfig` 주석에도 같은 내용을 남겨뒀다.

경로 매칭은 라우트 predicate와 **같은 `PathPattern` 매칭**을 쓴다. 단순 문자열 비교를 쓰면 `/api/v1/me%6dbers` 같은 인코딩 우회에서 "라우트는 매칭되는데 차단 판정은 안 되는" 상태가 생길 수 있다.

### 5-3. 부수적으로 고친 것

내부 키 비교를 `equals` → `MessageDigest.isEqual`(상수 시간)로 바꿨다. `equals`는 처음 다른 바이트에서 조기 반환하므로 응답 시간 차이로 키를 한 글자씩 좁혀갈 여지가 이론상 남는다.

### 5-4. 아직 남은 것

| 항목 | 내용 |
|---|---|
| **대칭키 공유** | `jwt.secret`이 두 서비스에 존재. RS256+JWKS 전환 시 해소 (별도 이슈) |
| **내부 키 = 단일 공유 시크릿** | 서비스가 늘어날수록 유출 반경이 커진다. 서비스별 키나 mTLS는 이번 범위 밖 |
| **개인정보 평문 저장** | `members.name`/`phone_number`, `addresses` 수령인 정보 — 레포 전체 정책 결정 대기(`security.md` S9) |
| **ADMIN 권한 검사 없음** | `X-User-Roles`를 전파하지만 이를 실제로 검사하는 코드는 아직 없다(`hasRole`/`@PreAuthorize` 0건). project-service 착수와 맞물림 |
| **레이트리밋 / CORS** | 게이트웨이 2차 범위 |
| **네트워크 수준 격리** | 지금 방어는 전부 애플리케이션 레벨이다. 운영에서는 서비스 포트가 게이트웨이에서만 도달 가능하도록 K8s NetworkPolicy 등으로 한 겹 더 막는 것이 정석 |

---

## 6. 테스트

전부 **스프링 컨텍스트를 띄우지 않는 순수 단위 테스트**다. `application-local.yml`이 `.gitignore` 대상이라 CI 체크아웃 트리에 없는데, `@SpringBootTest`를 쓰면 `jwt.secret`/`internal-api.key`가 미해석 상태로 `PlaceholderResolutionException`이 난다 — Phase 1에서 실제로 CI가 이 이유로 깨졌다.

| 테스트 | 검증 |
|---|---|
| `JwtHeaderGlobalFilterUnitTest` | 정상 주입, 토큰 없음 통과, `Authorization` 보존, 위조 헤더 제거, 토큰+위조헤더 동시 |
| `JwtHeaderGlobalFilterUnitExceptionTest` | 내부 경로 404(회원생성·소셜가입), 위조 서명, 만료, refresh 토큰 |
| `LoginUserArgumentResolverUnitTest`/`UnitExceptionTest` | `CurrentUser` 조립, 다중 role, 헤더 없음/형식 오류 401 |
| `InternalGatewaySecretFilterUnitTest`/`UnitExceptionTest` | 공개 API 통과, 키 일치/불일치, 내부 경로 |

토큰은 목이 아니라 **실제 HS256 서명**을 만들어 진짜 검증을 거치게 한다(`GatewayFilterFixture`).

라우팅 자체(yml 선언)는 테스트하지 않는다 — 선언형 설정이라 단위 테스트로 얻을 게 없다.

### 수동 E2E (게이트웨이 8080 + auth 8081 + member 8082)

```bash
# 1. 로그인 → accessToken
curl -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
     -d '{"email":"...","password":"..."}'
# 2. 토큰으로 프로필 조회 → 200
curl localhost:8080/api/v1/members/me -H "Authorization: Bearer $TOKEN"
# 3. 토큰 없이 X-User-Id만 위조 → 401
curl localhost:8080/api/v1/members/me -H "X-User-Id: <남의 UUID>"
# 4. 게이트웨이 경유 내부 엔드포인트 → 404
curl -X POST localhost:8080/api/v1/members -H 'Content-Type: application/json' -d '{}'
# 5. 게이트웨이 우회 직접 호출 + 헤더 위조 → 401
curl localhost:8082/api/v1/members/me -H "X-User-Id: <남의 UUID>"
```

3·4·5번이 이 작업의 존재 이유다 — 이전에는 셋 다 뚫렸다.

---

## 7. 다른 서비스가 이 구조에 올라타는 방법

새 Servlet 서비스를 게이트웨이 뒤에 붙일 때 필요한 것은 세 가지다.

1. `build.gradle`에 `implementation project(':modules:common-webmvc')`
2. 메인 클래스에 `@SpringBootApplication(scanBasePackages = "com.fundit")`
   — 없으면 `com.fundit.common.webmvc.*`의 공통 설정이 컴포넌트 스캔에 안 잡힌다
3. `application-{local,dev,prod}.yml`에 `internal-api.key`
   (dev/prod는 `${INTERNAL_API_KEY}`, 모든 서비스와 게이트웨이가 같은 값)

그리고 컨트롤러에서:

```java
@GetMapping("/me")
public XxxResponse getMe(@LoginUser CurrentUser user) {
    return service.find(user.id());
}
```

내부 전용 엔드포인트가 있다면 `InternalEndpoint` 빈으로 선언하고(member-service의 `InternalEndpointConfig` 참고), **게이트웨이 라우팅에서도 제외**해야 한다(§5-2).

> auth-service는 이 구조에 올리지 않았다. 토큰 발급자 본인이라 `@AuthenticationPrincipal` 기반 자체 검증을 유지하는 게 맞고, `X-User-Id`를 신뢰하는 지점이 하나도 없어 공통 플러밍이 할 일이 없다.

---

## 8. 알려진 이슈

- **`modules:common-webmvc`의 JaCoCo 커버리지가 49%로 80% 기준 미달.** 다만 이건 이번에 생긴 문제가 아니다 — 이 모듈은 지금까지 테스트가 0개(=0%)였고, 어떤 CI도 이 모듈의 `jacocoTestCoverageVerification`을 실행하지 않는다(서비스 CI는 각 서비스 번들만 측정). 이번에 인증 플러밍 테스트가 들어가며 0% → 49%가 됐다. 남은 미달분은 기존 `AbstractGlobalExceptionHandler`에 테스트가 없어서다. 별도로 정리할 것 — 특히 검증 오류 `detail` 배열의 JSON 형태를 고정하는 테스트가 레포 전체에 하나도 없다.

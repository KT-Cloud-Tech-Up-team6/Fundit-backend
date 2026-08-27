# 개발 워크플로우 가이드

## 개요

**Monorepo** 구조를 채택합니다. 모든 서비스가 하나의 레포지토리에 있고, **로컬에서는 전체를 그대로 클론**합니다 — sparse-checkout으로 담당 서비스만 받는 방식은 쓰지 않습니다. 2인 팀에서 한 명이 부재중일 때 다른 한 명이 어떤 서비스든 바로 열어서 대신 작업할 수 있어야 한다는 이유로 이미 정한 원칙입니다(로컬 sparse-checkout은 이 목적과 정면으로 충돌).

> CI(GitHub Actions)에서는 별도로 sparse-checkout을 씁니다 — 이건 로컬 정책과 무관하게 빌드 속도만을 위한 것입니다. 아래 "CI/CD 흐름"에서 다룹니다.

```
fundit-backend/                 ← 하나의 레포지토리, 전체를 클론
├── modules/common/             ← 전 서비스 공통
├── services/auth-service/
├── services/member-service/
├── services/order-service/
├── services/payment-service/
└── ...
```

---

## 브랜치 전략

```
main      ← 최종 배포 브랜치 (직접 push 금지, CLAUDE.md 규칙)
develop   ← 통합 브랜치 (PR을 통해서만 머지)
feature/* ← 기능 개발 브랜치 (develop에서 분기)
```

---

## 최초 세팅 (1회만)

```bash
git clone https://github.com/{org}/fundit-backend.git
cd fundit-backend
```

sparse-checkout 설정 없이 그냥 전체를 받으면 끝입니다.

---

## 개발 흐름

### 1. 작업 브랜치 생성
```bash
git checkout develop
git pull origin develop
git checkout -b feature/{기능명}

# 예시
git checkout -b feature/auth-login
```

### 2. 개발 후 커밋 (Conventional Commits, CLAUDE.md 규칙)
```bash
git add services/{담당 서비스}/
git commit -m "feat: 로그인 기능 구현"
```
- 커밋 제목 20자 이내, 세부 내용은 본문에 불릿으로.

### 3. Push 및 PR 생성
```bash
git push origin feature/{기능명}
```
GitHub에서 `feature/*` → `develop`으로 PR을 생성합니다. PR이 열리면 변경된 서비스의 CI가 자동 실행됩니다.

### 4. 최신 코드 받기
```bash
git pull origin develop
```
전체 클론이므로 그냥 pull하면 됩니다(sparse-checkout 범위 갱신 같은 추가 작업 없음).

---

## CI/CD

PR 생성 시 변경된 서비스의 CI가 자동 실행됩니다. 흐름·작성 방법·복붙 템플릿은 `docs/ci-workflow-guide.md` 참고.

---

## common 모듈 수정 시 주의사항

`modules/common` 변경은 **모든 서비스 빌드에 영향**을 줍니다. 수정 전 팀원에게 공유하고, 변경 후 각 서비스 담당자가 빌드 이상 여부를 확인해야 합니다. `modules:common`에는 응답 포맷 계약 클래스(`ApiResponse`, `ErrorCode` 등)만 두고 도메인/비즈니스 로직은 넣지 않습니다(CLAUDE.md 규칙).

---

## 새 서비스 추가 시

### 1. 디렉토리 생성 및 Spring Boot 구조 작성
`services/{서비스명}/` 아래 `presentation/application/domain/infrastructure` 계층 구조로 작성(CLAUDE.md 참고).

### 2. `settings.gradle`에 반드시 추가 — 자동 포함 아님
Gradle은 디렉토리가 존재한다고 자동으로 빌드 대상에 넣어주지 않습니다. **명시적으로 `include` 하지 않으면 그 서비스는 빌드에서 아예 빠집니다.** `settings.gradle`의 서비스 배열에 이름을 추가하세요.

```groovy
['auth-service', 'member-service', 'order-service', 'payment-service', '{새 서비스명}'].each { svc ->
    if (file("services/${svc}").exists()) {
        include "services:${svc}"
    }
}
```

추가 후 `./gradlew projects`로 실제로 인식됐는지 확인합니다.

### 3. `build.gradle` 작성 (아래 "서비스 build.gradle 작성 규칙" 참고)

### 4. `.github/workflows/ci-{서비스명}.yml` 생성
`docs/ci-workflow-guide.md`의 템플릿 사용.

> Node.js 등 Gradle을 쓰지 않는 서비스는 현재 계획에 없습니다. 실제로 필요해지면 그때 별도 규칙을 추가합니다(지금 미리 만들지 않음).

### 서비스별 빠른 참조

새 서비스 만들 때 아래 값들을 그대로 채우면 됩니다(포트는 `config-convention.md` 기준).

| 서비스 | `settings.gradle` 포함 이름 | CI 파일 | 로컬 앱 포트 | 로컬 DB 포트 |
|---|---|---|---|---|
| auth-service | `'auth-service'` | `ci-auth-service.yml` | 8081 | 5432 |
| member-service | `'member-service'` | `ci-member-service.yml` | 8082 | 5433 |
| project-service | `'project-service'` | `ci-project-service.yml` | 8083 | 5434 |
| order-service | `'order-service'` | `ci-order-service.yml` | 8084 | 5435 |
| payment-service | `'payment-service'` | `ci-payment-service.yml` | 8085 | 5436 |
| live-service | `'live-service'` | `ci-live-service.yml` | 8086 | 5437 |
| shipping-service | `'shipping-service'` | `ci-shipping-service.yml` | 8087 | 5438 |
| notification-service | `'notification-service'` | `ci-notification-service.yml` | 8088 | 5439 |
| platform:gateway-service | `'gateway-service'`(platform 배열) | `ci-gateway-service.yml` | 8080 | 해당 없음 |

---

## 서비스 `build.gradle` 작성 규칙

### 자동 제공 항목 (중복 선언 금지)

아래는 루트 `build.gradle` 또는 `modules/common`에서 이미 제공되므로 서비스별 `build.gradle`에 다시 선언하지 않습니다.

| 항목 | 출처 |
|---|---|
| Java 25 (toolchain) | 루트 `build.gradle` |
| Spring Boot BOM (`spring-boot-dependencies:4.1.1`), Spring Modulith BOM, Spring Cloud BOM | 루트 `build.gradle` |
| `spring-boot-starter-test`(JUnit 5, Mockito 포함), `useJUnitPlatform()` | 루트 `build.gradle` |
| Lombok(`compileOnly` + `annotationProcessor`) | 루트 `build.gradle` |
| Spring Boot 플러그인(`bootJar`) | 루트 `build.gradle` (`services/*`, `platform/*`에 자동 적용) |
| JPA/Flyway/PostgreSQL 드라이버, Testcontainers(postgresql·junit-jupiter) | 루트 `build.gradle` (`:services:` 전체 공통 블록) |
| 응답 포맷 계약 클래스(`ErrorResponse`, `PageResponse`, `ErrorCode` 인터페이스, `BusinessException`) | `modules:common` (Spring 의존성 전혀 없는 순수 Java) |

> ⚠️ `modules:common`은 Spring을 전혀 의존하지 않습니다 — `spring-web`조차 없습니다. `ErrorCode.getHttpStatus()`가 `int`를 반환하도록 확정하면서 Spring 타입 자체가 필요 없어졌기 때문입니다(성공 응답도 래퍼 없이 DTO 그대로 반환하기로 했으니 `ApiResponse<T>` 같은 것도 없습니다). 그래서 REST 컨트롤러가 필요한 서비스는 아래처럼 **`spring-boot-starter-web`을 직접 선언해야** 합니다 — `common`을 통해 전파되는 게 하나도 없습니다.

### 필수 선언 항목

```groovy
dependencies {
    implementation project(':modules:common')          // 항상 포함
    implementation 'org.springframework.boot:spring-boot-starter-web'  // REST 서비스는 직접 선언
}
```

### 작성 예시

```groovy
// services/auth-service/build.gradle
dependencies {
    implementation project(':modules:common')
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'  // 이 서비스만 필요하면 여기에
    implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
    runtimeOnly    'io.jsonwebtoken:jjwt-impl:0.12.6'
    runtimeOnly    'io.jsonwebtoken:jjwt-jackson:0.12.6'
}
```

> `platform:gateway-service`는 예외입니다 — 리액티브 스택(`spring-cloud-starter-gateway`)을 쓰므로 `spring-boot-starter-web`을 선언하면 안 됩니다(이전 대화에서 확정, MVC와 리액티브 스택 충돌).
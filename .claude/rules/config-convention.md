---
paths:
  - "**/src/main/resources/application*.yml"
  - "**/Dockerfile"
  - "**/docker-compose.yml"
---

# 환경 설정(Config) 컨벤션

## 파일 분리 — 4개 고정
서비스마다 아래 4개 파일로 나눈다. 하나로 몰아넣지 않는다.

| 파일 | 커밋 여부 | 용도 |
|---|---|---|
| `application.yml` | O | 프로필 전환, 공통 설정(Jackson, actuator 등) |
| `application-local.yml` | **X** (`.gitignore` 등록됨) | 로컬 개발자 PC |
| `application-dev.yml` | O | 공유 개발서버 |
| `application-prod.yml` | O, **직접 수정 금지**(CLAUDE.md 참고, 배포 파이프라인에서만 관리) | 운영 |

## 시크릿 하드코딩 금지 (security.md S9 연동)
`application-dev.yml`/`application-prod.yml`에는 실제 비밀번호·키 값을 절대 커밋하지 않는다. `${DB_PASSWORD}` 같은 환경변수 참조만 커밋하고, 실제 값은 K8s Secret(CNPG 접속정보 포함) 또는 배포 파이프라인이 주입한다.

## 로컬 DB — 컨테이너 하나, 데이터베이스는 서비스별로
서비스마다 별도 데이터베이스를 쓰지만(진짜 물리적으로 분리, 스키마 공유 아님), 로컬 개발 PC 부담을 줄이기 위해 **Postgres 컨테이너는 하나만** 띄우고 그 안에 서비스 수만큼 데이터베이스를 만든다. `docker-compose.yml` + init 스크립트로 구성한다.

## ddl-auto 정책
- local/dev: `none` (스키마는 Flyway가 관리)
- prod: `validate` (스키마 불일치 시 기동 자체를 막아 사고 예방)

## 애플리케이션 포트 — 로컬에서만 서비스별로 다르게
K8s(dev/prod)에서는 파드마다 네트워크 네임스페이스가 분리되어 전 서비스가 `8080`을 그대로 써도 충돌하지 않는다. 그래서 `server.port`는 공통 `application.yml`에서 `8080`으로 두고, **로컬에서 여러 서비스를 동시에 띄울 가능성에 대비해 `application-local.yml`에서만 서비스별로 덮어쓴다.**

| 서비스 | 로컬 앱 포트 |
|---|---|
| platform:gateway-service | 8080 |
| auth-service | 8081 |
| member-service | 8082 |
| project-service | 8083 |
| order-service | 8084 |
| payment-service | 8085 |
| live-service | 8086 |
| shipping-service | 8087 |
| notification-service | 8088 |

```yaml
# services/auth-service/src/main/resources/application-local.yml
server:
  port: 8081
```

멀티스테이지 빌드로 작성한다. 빌드 단계와 실행 단계의 이미지를 분리해서, 최종 이미지에 Gradle·소스코드가 남지 않게 한다.

```dockerfile
# 1단계: 빌드
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :services:{service}:bootJar --no-daemon

# 2단계: 실행 (JDK가 아니라 JRE만 — 이미지 용량 최소화)
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd -r -u 1000 appuser  # root로 실행하지 않음(S4 인가 원칙과 동일한 최소권한 정신)
COPY --from=builder /app/services/{service}/build/libs/*.jar app.jar
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- base 이미지는 Java 25에 맞춰 `eclipse-temurin:25-jdk`(빌드)/`eclipse-temurin:25-jre`(실행)로 고정한다.
- 컨테이너를 root로 실행하지 않는다.
- `.dockerignore`에 `build/`, `.gradle/`, `*.log` 등 불필요한 파일을 제외해 빌드 컨텍스트를 가볍게 유지한다(`.gitignore`와 겹치는 항목이 많음).

## docker-compose.yml — 서비스마다 개별로, 로컬 전용
`services/{service}/docker-compose.yml`로 **서비스마다 따로 둔다** — 컨테이너를 공유하지 않는다. "DB는 서비스마다 물리적으로 분리한다"는 원칙을 로컬 환경에서도 그대로 지키기 위함이다.

dev/prod 환경은 인프라팀이 K8s + CNPG로 관리하므로, docker-compose에는 별도 `.dev.yml` 계층을 두지 않는다 — 이 파일은 로컬 개발 전용이다.

```yaml
services:
  postgres:
    image: postgres:16
    container_name: fundit-{service}-postgres
    env_file: .env
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USERNAME}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "127.0.0.1:{PORT}:5432"
    volumes:
      - {service}_postgres_data:/var/lib/postgresql/data
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USERNAME}"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - fundit-{service}-net

volumes:
  {service}_postgres_data:

networks:
  fundit-{service}-net:
```

- 자격증명은 `.env`(gitignore 대상, `.env.example`만 커밋)로 주입 — MySQL 참고 예시의 `MYSQL_*` 대신 Postgres `POSTGRES_*` 환경변수를 쓴다.
- **호스트 포트는 서비스마다 고정 배정**해서 여러 서비스를 동시에 로컬에 띄워도 충돌하지 않게 한다(서비스 하나만 작업할 땐 상관없지만, 두 서비스 간 연동을 로컬에서 같이 테스트할 때를 대비):

  | 서비스 | 호스트 포트 |
  |---|---|
  | auth-service | 5432 |
  | member-service | 5433 |
  | project-service | 5434 |
  | order-service | 5435 |
  | payment-service | 5436 |
  | live-service | 5437 |
  | shipping-service | 5438 |
  | notification-service | 5439 |

> CI(GitHub Actions)에서는 이 파일을 쓰지 않는다 — Testcontainers가 테스트 실행 중에 자체적으로 컨테이너를 관리한다(`test-convention.md` 참고). 로컬 편의용 정의와 테스트 인프라 정의를 같은 파일로 섞지 않는다.
# CI 워크플로우 작성 가이드

> 서비스마다 `.github/workflows/ci-{service}.yml`을 만들 때, 기존 예시(`ci-auth-service.yml` 등)를 복붙해서 서비스명만 바꾸는 걸로는 부족한 지점들을 정리한 체크리스트입니다. 새 서비스 CI를 만들거나, 다른 프로젝트에서 가져온 예시를 이 프로젝트에 맞게 고칠 때 참고하세요.

## 전체 흐름

```
feature/* 브랜치 push
        ↓
PR → develop 생성
        ↓
경로 필터(paths)로 변경된 서비스의 CI만 자동 실행
  - CI 러너 안에서만 sparse-checkout으로 해당 서비스 + modules/common만 클론(속도 최적화, 로컬 개발 정책과는 무관)
  - ./gradlew :services:{서비스명}:build :services:{서비스명}:jacocoTestReport
  - 통합테스트는 Testcontainers가 자체적으로 DB 컨테이너를 띄우고 내림
  - 빌드 성공 + 커버리지 기준(80%, test-convention.md) 통과 시 JAR 아티팩트 저장
        ↓
CI 통과 후 develop 머지 가능
```

Git 브랜치 전략·클론·커밋·PR 흐름 자체는 `docs/development-workflow-guide.md` 참고.

## 복붙 시 반드시 바꿔야 할 것

| 항목 | 바꿀 내용 |
|---|---|
| 워크플로우 `name`, 파일명 | `CI - {service}`, `ci-{service}.yml` |
| `on.push/pull_request.paths` | `services/{service}/**` (아래 "트리거 경로" 참고) |
| `sparse-checkout` 목록 | `services/{service}` (아래 "sparse-checkout" 참고) |
| Gradle 태스크의 서비스 경로 | `:services:{service}:...` |
| Upload Artifact의 `name`, `path` | 서비스명 반영 |

## 트리거 경로(`paths`) — 자기 서비스 폴더만으로는 부족

자기 서비스 경로만 넣으면, `modules/common`이나 루트 `build.gradle`이 바뀌었을 때 이 워크플로우가 안 돌아갑니다. 공용 모듈 변경도 감지하도록 아래를 항상 같이 넣습니다.

```yaml
paths:
  - 'services/{service}/**'
  - 'modules/common/**'
  - 'build.gradle'
  - 'settings.gradle'
```

## DB/메시징 서비스 컨테이너: 넣지 않음 (신규 추가 시에도 CI 변경 없음)

다른 프로젝트에서 가져온 CI 예시에 `services: mysql:` / `services: rabbitmq:` 같은 GitHub Actions 서비스 컨테이너 블록이 있어도 **그대로 복붙하지 않습니다.** 새 DB(또는 나중에 메시징)를 쓰게 되어도 이 CI yml 자체를 고칠 필요가 없습니다.

**이유**: `test-convention.md`에 통합테스트는 Testcontainers로 하기로 확정되어 있고, Testcontainers는 GH Actions 러너에 기본 설치된 Docker로 테스트 실행 중에 자체적으로 컨테이너를 띄우고 내립니다. 미리 고정된 서비스 컨테이너를 또 띄우면 두 가지 DB 프로비저닝 방식이 공존하게 되고, 실제로는 안 쓰이는 컨테이너가 CI 시간만 잡아먹습니다. CI yml을 계속 고쳐야 하는 방식보다 유지보수가 적게 드는 게 이 방식을 택한 이유입니다.

**새 DB/컨테이너가 필요해졌을 때 실제로 할 일**:
1. CI yml — 그대로 둔다.
2. 서비스 `build.gradle` — 테스트 의존성에 필요한 Testcontainers 모듈만 추가(`testcontainers-postgresql` 등).
3. 테스트 코드 — `@Container` + `@ServiceConnection`으로 연결하면 끝.

- DB: 예시에 MySQL이 있어도 이 프로젝트는 PostgreSQL 기준입니다.
- 메시징(Kafka/RabbitMQ): 기술 자체가 아직 미확정이니, 확정 전까지는 넣지 않습니다.

## sparse-checkout — 실제 존재하는 경로만

다른 프로젝트(특히 예전 참고 프로젝트) 예시를 가져올 때 `libs/bds-events`, `modules/messaging`처럼 **이 프로젝트에 없는 경로**가 섞여 들어오기 쉽습니다. 지금 실제로 존재하는 경로만 남깁니다.

```yaml
sparse-checkout: |
  services/{service}
  modules/common
  build.gradle
  settings.gradle
  gradlew
  gradle
```

## 액션 버전 — 예시가 오래됐을 수 있음

`actions/checkout`, `actions/setup-java` 같은 액션은 버전이 빠르게 올라갑니다. 예시 문서를 그대로 복붙하기 전에, 그 시점 기준 최신 안정 버전을 한 번 확인하세요(예: `setup-java@v4`는 deprecated 처리된 적이 있음).

```yaml
- uses: actions/checkout@v7        # 예시 그대로 쓰지 말고 최신 버전 확인
- uses: actions/setup-java@v6
  with:
    java-version: '25'
    distribution: 'temurin'
    cache: 'gradle'
```

## JaCoCo — CI 작성 전에 먼저 확인할 것

`jacocoTestReport`/`jacocoTestCoverageVerification` 태스크를 CI에서 돌리려면, **루트 `build.gradle`에 JaCoCo 플러그인이 이미 적용되어 있어야** 합니다(`subprojects {}` 블록). 없으면 CI가 "그런 태스크 없음" 에러로 바로 실패합니다. CI 작성/복붙 전에 `./gradlew :services:{service}:jacocoTestReport`를 로컬에서 먼저 돌려보고 태스크가 존재하는지 확인하세요.

## 이 CI가 하지 않는 것 — Docker 이미지 빌드/배포

이 CI 워크플로우의 범위는 **빌드 + 테스트 + jar 아티팩트 업로드까지**입니다. Docker 이미지 빌드, ECR push, 배포는 인프라팀 파이프라인 소관입니다(인프라팀 문서 "개발팀: Dockerfile 작성 / 인프라팀: Image Build·ECR·배포" 기준). 예시 문서에 이미지 빌드/푸시 단계가 있어도, 인프라팀과 별도로 확인하기 전까지는 이 워크플로우에 추가하지 않습니다.

## 복붙 템플릿

`{service}`를 실제 서비스명(예: `auth-service`)으로 전부 치환해서 쓰세요. 위 체크리스트가 전부 반영되어 있습니다.

```yaml
name: CI - {service}

on:
  push:
    branches: [main, develop]
    paths:
      - 'services/{service}/**'
      - 'modules/common/**'
      - 'build.gradle'
      - 'settings.gradle'
  pull_request:
    branches: [develop, main]
    paths:
      - 'services/{service}/**'
      - 'modules/common/**'
      - 'build.gradle'
      - 'settings.gradle'

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    # DB/메시징 서비스 컨테이너를 여기서 미리 띄우지 않는다 — 통합테스트가
    # Testcontainers로 필요한 컨테이너를 직접 관리한다(test-convention.md 기준).

    steps:
      - uses: actions/checkout@v7
        with:
          sparse-checkout: |
            services/{service}
            modules/common
            build.gradle
            settings.gradle
            gradlew
            gradle

      - uses: actions/setup-java@v6
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Grant execute permission
        run: chmod +x gradlew

      - name: Build and Test
        run: ./gradlew :services:{service}:clean :services:{service}:build :services:{service}:jacocoTestReport :services:{service}:jacocoTestCoverageVerification --stacktrace

      - name: Upload Coverage Report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report-{service}
          path: services/{service}/build/reports/jacoco/test/html/

      - name: Upload Artifact
        uses: actions/upload-artifact@v4
        with:
          name: {service}
          path: services/{service}/build/libs/*[!plain].jar
```

## 참고 구현

`.github/workflows/ci-auth-service.yml` — 위 템플릿을 실제로 적용한 예시입니다.

> 템플릿을 그대로 복붙하시더라도, 액션 버전(`checkout`, `setup-java`)만큼은 매번 최신 안정 버전을 다시 확인하세요. 시간이 지나면 여기 적힌 버전도 예전 것이 됩니다 — "이 문서에 있던 버전"이 "지금 최신 버전"이라는 보장은 없습니다.
# 프로젝트 개요
- fundit-backend: Spring Boot 4.1.1 / Java 25, Gradle 멀티모듈(MSA)
- 서비스: `services:auth-service`, `services:member-service`, `services:order-service`, `services:payment-service` (착수 순) 외 `project/live/shipping/notification/search-service`, `platform:gateway-service` 예정
- 각 서비스 내부는 계층형 아키텍처로 구성: `presentation → application → domain → infrastructure`
  - `presentation`: REST 컨트롤러, 요청/응답 DTO
  - `application`: 유스케이스/애플리케이션 서비스, 아웃바운드 포트 인터페이스
  - `domain`: 도메인 모델(엔티티·값 객체), 도메인 예외, 레포지토리 인터페이스(포트)
  - `infrastructure`: JPA 엔티티, 매퍼, 리포지토리 구현체(어댑터), 외부 API 연동, 설정(Bean Config)
- 공통 규칙: `.claude/rules/`(`api-convention.md`, `error-handling.md`, `test-convention.md`, `security.md`, `persistence-convention.md`, `config-convention.md`)
- 프로젝트 전반 문서: 루트 `docs/`(`PRD.md` — 전체 요구사항 정의서, `development-workflow-guide.md` — 브랜치 전략/Gradle 규칙, `ci-workflow-guide.md` — CI 작성 가이드+템플릿)
- 서비스별 문서: `services/{service}/docs/`(API 명세서, 예: `auth-domain-api-spec.md`, `member-domain-api-spec.md`). 서비스별 `CLAUDE.md`는 아직 없음 — 해당 서비스 개발 착수 시점에 추가 예정

# 빌드/테스트 명령
- 전체 빌드: `./gradlew build`
- 특정 서비스만 빌드: `./gradlew :services:{service}:build`
- 전체 테스트: `./gradlew test`
- 특정 서비스만 테스트: `./gradlew :services:{service}:test`
- 현재 인식되는 서비스 목록 확인: `./gradlew projects`

# 절대 하지 말아야 할 것(주의사항)
- `application-prod.yml`을 직접 수정하지 말 것 (배포 파이프라인에서 관리)
- `src/main/resources/db/migration/V*.sql` 기존 파일 수정 금지, 새 버전만 추가
- main 브랜치에 강제 푸시 금지
- 다른 서비스의 DB 테이블에 직접 접근하지 말 것 (API 또는 이벤트로만 통신)
- `modules:common`에 도메인/비즈니스 로직을 추가하지 말 것 (응답 포맷 계약 클래스만 유지)
- 서비스 간 동기 호출에는 반드시 타임아웃을 설정할 것 (프레임워크 기본값 그대로 두지 말 것)
- `domain` 패키지 클래스에 JPA/Spring 애노테이션을 붙이지 말 것 (`persistence-convention.md` 참고, `infrastructure`에서만 매핑)

# 코드 및 커밋 컨벤션
- Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`)
- 커밋 제목은 20자 이내로 작성
- 세부 내용은 본문에 불릿으로 추가
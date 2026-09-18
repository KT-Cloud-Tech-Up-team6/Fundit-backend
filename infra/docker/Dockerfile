# 전 서비스 공용 런타임 이미지.
#
# jar는 이미지 안에서 빌드하지 않고 CI가 올린 artifact를 받아 복사한다(cd-ecr-push.yml).
# 그래서 이 파일만으로는 단독 빌드가 되지 않는다 — JAR_FILE 경로가 필요하다.

# 루트 build.gradle의 toolchain(Java 25)과 일치시킨다. latest를 쓰지 않는다 —
# 베이스가 조용히 올라가면 재현되지 않는 기동 실패가 생긴다.
FROM eclipse-temurin:25-jre-alpine

ARG JAR_FILE
ARG SERVER_PORT=8080

# alpine은 adduser 문법이 다르다(-D: 비밀번호 없음, -H: 홈 미생성)
RUN adduser -D -H -u 10001 fundit
USER fundit

WORKDIR /app
COPY --chown=fundit:fundit ${JAR_FILE} app.jar
EXPOSE ${SERVER_PORT}

# exec 형식이라 JVM이 PID 1로 뜬다 — docker stop의 SIGTERM이 JVM에 직접 전달돼
# graceful shutdown이 된다. 쉘 형식이면 쉘이 시그널을 삼켜 10초 뒤 SIGKILL이다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

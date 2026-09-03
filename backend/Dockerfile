# syntax=docker/dockerfile:1
# API 서버(Spring Boot). 기준 버전은 docs/dev-environment.md — 여기 이미지 태그가 곧 팀 표준이다.
#
# 두 단계다. 빌드는 JDK + Gradle 래퍼로 bootJar 를 만들고, 실행 이미지에는 JRE 와 jar 하나만 남긴다.
# Gradle 캐시(~/.gradle)는 BuildKit 캐시 마운트에 둔다 — 소스 한 줄 고칠 때마다 의존성을 다시 받지 않게.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# 의존성 해석은 소스보다 먼저 — 빌드 파일이 안 바뀌면 이 레이어는 캐시에서 온다.
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -q dependencies > /dev/null

COPY src ./src
# 테스트는 여기서 안 돈다. Testcontainers 가 Docker 데몬을 요구해 이미지 빌드 안에서는 못 돌고,
# 테스트 게이트는 로컬 ./gradlew test 와 CI 몫이다.
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -q bootJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# root 로 띄우지 않는다. 컨테이너가 뚫려도 이미지 안의 파일 권한이 한 겹 남는다.
RUN addgroup -S careerfit && adduser -S -G careerfit careerfit
COPY --from=build --chown=careerfit:careerfit /src/build/libs/*.jar app.jar
USER careerfit

# docker 프로필은 application.yml 의 ../.env 강제 import 를 끈다 — 컨테이너는 환경변수(compose env_file)로 받는다.
ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

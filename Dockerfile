# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy@sha256:4b1a74fbffbe6b4e7b6f0171a472478282fb463790a51edf10e465d578f22b69 AS build
WORKDIR /workspace

COPY --chmod=0755 gradlew ./
COPY gradle/ gradle/
COPY settings.gradle build.gradle ./
COPY auth/ auth/
COPY src/ src/

RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon :bootJar

FROM eclipse-temurin:21-jre-jammy@sha256:1eac8b8fee11ca7d6725a48bc5d41992ed1561504ebae25490d2d062847e8269
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar /app/app.jar

ENV SERVER_PORT=8081
EXPOSE 8081
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

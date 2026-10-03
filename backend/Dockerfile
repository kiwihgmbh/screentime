# Build and run the backend.
#
# The tests are not run here. They need a Docker daemon of their own for
# Testcontainers, and a build that silently skipped them would be worse than
# one that never claimed to run them. `./mvnw verify` is the gate, before the
# image is built.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# dependencies first, so a change to the sources does not re-download the world
COPY pom.xml .
COPY .mvn/ .mvn/
RUN mvn -B -q dependency:go-offline

COPY src/ src/
RUN mvn -B -q -DskipTests package \
    && mv target/screentime-*.jar target/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime

# curl is here for the health check and nothing else
RUN apk add --no-cache curl tzdata \
    && addgroup -S screentime \
    && adduser -S -G screentime screentime

WORKDIR /app
COPY --from=build --chown=screentime:screentime /build/target/app.jar app.jar

USER screentime
EXPOSE 8080

# Container aware defaults. The heap is a share of whatever the host allows
# rather than a fixed number, because this runs on anything from a Pi to a VPS.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -Djava.security.egd=file:/dev/urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'

ENTRYPOINT ["java", "-jar", "app.jar"]

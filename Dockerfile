# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------------------------
# Build stage
# ---------------------------------------------------------------------------------------------
# The build runs inside the image, so the result does not depend on what happens to be installed
# on the machine that built it. The wrapper pins Gradle; the base image pins the JDK.
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

# Dependencies change far less often than source. Copying the build files first means a
# source-only change reuses the cached dependency layer instead of downloading the world again.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY src ./src

# Tests are not run here. They need PostgreSQL and MinIO containers, which belong in CI where
# services can be started alongside. A build that silently skipped them would be worse than one
# that never claimed to run them.
RUN ./gradlew --no-daemon clean bootJar -x test

# The jar is built under a fixed name (see build.gradle), so the entrypoint below never has to
# change for a version bump.
#
# Layered extraction was tried here and reverted: it splits the image into layers that change at
# different rates, which is a real build-cache win, but the extracted layout did not launch and
# an image that does not start is worse than one that pulls a few megabytes more. Noted in
# PROGRESS.md as an optimisation to revisit with the time to get it right.

# ---------------------------------------------------------------------------------------------
# Runtime stage
# ---------------------------------------------------------------------------------------------
# A JRE, not a JDK: the compiler, the debugger and the tooling are attack surface the running
# application has no use for.
FROM eclipse-temurin:21-jre-jammy AS runtime

# curl is needed by the health check below; nothing else is installed.
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/*

# Never root. A container process running as root is root on the host kernel, so a container
# escape would start from the strongest possible position.
RUN groupadd --system --gid 1001 bankcore \
    && useradd --system --uid 1001 --gid bankcore --home /app --shell /usr/sbin/nologin bankcore

WORKDIR /app

COPY --from=build --chown=bankcore:bankcore /workspace/build/libs/bankcore.jar ./bankcore.jar

USER bankcore

EXPOSE 8080

# Container-aware defaults. Without MaxRAMPercentage the JVM sizes its heap from the host's
# memory and is then killed by a container limit it never knew about.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=prod

# Readiness, not liveness: this answers "can it take traffic", which is what an orchestrator needs
# before routing to it. start-period covers the time Flyway spends migrating.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
    CMD curl --fail --silent http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "bankcore.jar"]

# syntax=docker/dockerfile:1

# ---- Stage 1: build the fat jar -------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Copy the wrapper + pom first. We don't pre-warm with dependency:go-offline:
# it eagerly tries to resolve optional transitive artifacts (e.g. batik's
# slideshow module) that aren't published to Central and would fail the build.
# package resolves exactly what's needed instead.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Sources, then build (tests skipped — this image is for running, not CI).
COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# ---- Stage 2: slim runtime ------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# curl is used by the compose healthcheck to probe the actuator endpoint.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as a non-root user.
RUN groupadd --system tcket && useradd --system --gid tcket tcket

COPY --from=build /build/target/tCketManageBackend-*.jar app.jar
RUN chown -R tcket:tcket /app
USER tcket

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

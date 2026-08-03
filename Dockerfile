# syntax=docker/dockerfile:1

# ---- Build stage -----------------------------------------------------------
# Uses the Maven base image, so the build needs no wrapper bootstrap or JDK-only download
# step (`.mvn/` and `mvnw` are excluded from the context by .dockerignore).
# Tests are intentionally NOT run here: they rely on Testcontainers/Docker and belong
# in the inner-loop / CI, not the image build. See docs/WORKFLOW.md.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Warm the dependency cache as its own layer so source changes don't re-download deps.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Copy the repackaged Spring Boot jar (the *.jar.original is excluded by the suffix).
COPY --from=build /build/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

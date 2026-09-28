# Multi-stage build: compile with the Gradle JDK image, run on a slim JRE.
# Build from the signaling-server/ directory:  docker build -t signaling-server .
FROM gradle:8.9-jdk17 AS build
WORKDIR /home/gradle/src
COPY build.gradle.kts settings.gradle.kts* ./
COPY src ./src
RUN gradle installDist --no-daemon

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /home/gradle/src/build/install/signaling-server /app
# Cloud Run sets $PORT; Application.kt reads it via System.getenv("PORT").
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/bin/signaling-server"]

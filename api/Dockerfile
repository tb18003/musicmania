# Build context: the api/ directory.  docker build -t musicmania-api api/
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
# The repository's release version (web/package.json): the build context is api/ only, so it is passed in.
ARG APP_VERSION
COPY . .
RUN test -n "$APP_VERSION" || (echo "Pass --build-arg APP_VERSION=x.y.z" >&2; exit 1)
RUN chmod +x gradlew && ./gradlew --no-daemon --no-configuration-cache -PappVersion="$APP_VERSION" buildFatJar

# Minimal runtime: no shell, no package manager, runs as an unprivileged user.
FROM gcr.io/distroless/java21-debian12:nonroot
COPY --from=build /src/build/libs/musicmania-api.jar /app/musicmania-api.jar
USER nonroot
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/musicmania-api.jar"]

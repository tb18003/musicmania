# Build context: the api/ directory.  docker build -t musicmania-api api/
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon --no-configuration-cache buildFatJar

# Minimal runtime: no shell, no package manager, runs as an unprivileged user.
FROM gcr.io/distroless/java21-debian12:nonroot
COPY --from=build /src/build/libs/musicmania-api.jar /app/musicmania-api.jar
USER nonroot
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/musicmania-api.jar"]

# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew .
COPY gradle gradle
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY room-service/build.gradle.kts room-service/
COPY room-service/src room-service/src
RUN chmod +x gradlew
RUN ./gradlew :room-service:bootJar -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=build /workspace/room-service/build/libs/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]

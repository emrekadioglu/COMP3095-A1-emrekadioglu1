# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew .
COPY gradle gradle
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY user-service/build.gradle.kts user-service/
COPY user-service/src user-service/src
RUN sed -i "s/\r$//" gradlew && chmod +x gradlew
RUN sh ./gradlew :user-service:bootJar -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=build /workspace/user-service/build/libs/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]

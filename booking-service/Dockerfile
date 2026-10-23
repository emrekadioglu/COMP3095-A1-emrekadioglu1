# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew .
COPY gradle gradle
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY booking-service/build.gradle.kts booking-service/
COPY booking-service/src booking-service/src
RUN sed -i "s/\r$//" gradlew && chmod +x gradlew
RUN sh ./gradlew :booking-service:bootJar -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=build /workspace/booking-service/build/libs/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]

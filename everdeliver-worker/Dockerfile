# Stage 1: Build the JAR
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the gradle wrapper and configuration
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Copy the modules (needed for multi-module dependencies)
COPY everdeliver-common everdeliver-common
COPY everdeliver-worker everdeliver-worker

# Build the specific module
RUN ./gradlew :everdeliver-worker:build -x test

# Stage 2: Run the JAR
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/everdeliver-worker/build/libs/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
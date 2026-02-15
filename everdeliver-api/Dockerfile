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
COPY everdeliver-api everdeliver-api

# Build the specific module
RUN ./gradlew :everdeliver-api:build -x test

# Stage 2: Run the JAR
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/everdeliver-api/build/libs/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
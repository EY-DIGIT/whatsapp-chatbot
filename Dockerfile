# Multi-stage Dockerfile for building and running the Spring Boot application
# Stage 1: build with Maven
FROM maven:3.8.8-eclipse-temurin-17 AS builder
WORKDIR /workspace

# copy only what is needed for a maven build (speeds up cache usage)
COPY pom.xml ./
COPY src ./src

# Build the application (skip tests to speed up image build; remove -DskipTests if you want tests)
RUN mvn -B -DskipTests package

# Stage 2: runtime image with a small JRE
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy built jar from builder stage. The Maven build produces target/*.jar
COPY --from=builder /workspace/target/*.jar app.jar

EXPOSE 8080

# Run the jar with reasonable memory defaults; allow overriding memory via JAVA_OPTS
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar"]


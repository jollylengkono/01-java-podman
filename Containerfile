# Stage 1: compile — full JDK + Maven, discarded after this stage
FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
# Copy pom.xml before source so this layer is cached when only src/ changes
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: runtime — JRE only (~90 MB), no build tools
FROM docker.io/library/eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

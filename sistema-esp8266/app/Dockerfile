# syntax=docker/dockerfile:1.6
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:17-jre-alpine AS runtime
RUN apk add --no-cache curl
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/app-0.0.1-SNAPSHOT.jar app.jar
RUN chown -R app:app /app
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD curl -sf http://127.0.0.1:8080/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

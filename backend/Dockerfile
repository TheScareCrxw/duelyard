# Spring Boot production image. Build context: backend/
#   docker build -t duelyard-backend .
# Configuration comes from environment variables (DB_*, COGNITO_*, APP_CORS_ALLOWED_ORIGINS, ...).

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
# Tests run in CI (they need no Docker: embedded PostgreSQL); skip them in the image build.
RUN ./mvnw -q -B -DskipTests package && cp target/backend-*.jar app.jar

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /src/app.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Health: GET /actuator/health (used by the load balancer target group).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw --batch-mode -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system campus && useradd --system --gid campus --home-dir /app campus
WORKDIR /app
COPY --from=build --chown=campus:campus /workspace/target/campus-service-*.jar app.jar
COPY --chown=campus:campus scripts/docker-backend-health.sh /app/health.sh
USER campus
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD ["bash", "/app/health.sh"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

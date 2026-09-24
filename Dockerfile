FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system campus && useradd --system --gid campus --home-dir /app campus
WORKDIR /app
COPY --from=build --chown=campus:campus /workspace/target/campus-service-*.jar app.jar
USER campus
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

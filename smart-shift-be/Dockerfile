FROM eclipse-temurin:17-jdk-noble AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B --no-transfer-progress dependency:go-offline

COPY src src
RUN ./mvnw clean package -DskipTests -B && cp target/*.jar target/app.jar

FROM eclipse-temurin:17-jre-noble

WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

COPY --from=build /workspace/target/app.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
    CMD curl --fail --silent --max-time 3 http://127.0.0.1:8080/readyz > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]


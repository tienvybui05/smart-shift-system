# Run Maven -Pcoverage-e2e clean verify before building this image.
# The application JAR and JaCoCo report therefore use the exact same class files.
FROM eclipse-temurin:17-jre-noble
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring
COPY target/smart-shift-be-0.0.1-SNAPSHOT.jar app.jar
COPY target/coverage-agent/jacoco-agent.jar jacoco-agent.jar
USER spring:spring
EXPOSE 8080 26300
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
    CMD curl --fail --silent --max-time 3 http://127.0.0.1:8080/readyz > /dev/null || exit 1
ENTRYPOINT ["java", "-javaagent:/app/jacoco-agent.jar=output=tcpserver,address=*,port=26300,dumponexit=false", "-jar", "app.jar"]

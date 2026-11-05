FROM eclipse-temurin:17-jdk-noble
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B --no-transfer-progress dependency:go-offline
COPY src src
CMD ["./mvnw", "-B", "--no-transfer-progress", "clean", "verify"]

FROM eclipse-temurin:17-jdk-noble AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B '-Dtest=*Test' test dependency:copy-dependencies -DincludeScope=test -DoutputDirectory=target/benchmark-lib

FROM eclipse-temurin:17-jre-noble
WORKDIR /benchmark
COPY --from=build /workspace/target/classes classes
COPY --from=build /workspace/target/test-classes test-classes
COPY --from=build /workspace/target/benchmark-lib lib
ENTRYPOINT ["java", "-cp", "/benchmark/test-classes:/benchmark/classes:/benchmark/lib/*", "com.smartshift.benchmark.BenchmarkCli"]

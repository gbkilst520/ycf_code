FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY . .

RUN mvn -q -DskipTests -pl http-service -am install \
    && mvn -q -DskipTests -f http-service/pom.xml dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency

FROM maven:3.9.9-eclipse-temurin-17
WORKDIR /app

COPY --from=build /workspace/http-service/target/classes /app/classes
COPY --from=build /workspace/http-service/target/dependency /app/dependency

EXPOSE 8081
ENTRYPOINT ["java", "-cp", "/app/classes:/app/dependency/*", "com.ycf.http.HttpServiceApplication"]

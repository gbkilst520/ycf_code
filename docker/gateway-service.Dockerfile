FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY . .

RUN mvn -q -DskipTests -pl gateway-service -am install \
    && mvn -q -DskipTests -f gateway-service/pom.xml dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency

FROM maven:3.9.9-eclipse-temurin-17
WORKDIR /app

COPY --from=build /workspace/gateway-service/target/classes /app/classes
COPY --from=build /workspace/gateway-service/target/dependency /app/dependency
COPY docker/gateway-config.docker.yaml /app/classes/gateway-config.yaml

EXPOSE 8080
ENTRYPOINT ["java", "-Dgateway.config=gateway-config.yaml", "-cp", "/app/classes:/app/dependency/*", "com.ycf.gateway.bootstrap.GatewayBootstrap"]

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
COPY gateway/pom.xml gateway/pom.xml
COPY api-server/pom.xml api-server/pom.xml
COPY product-service/pom.xml product-service/pom.xml
COPY pricing-service/pom.xml pricing-service/pom.xml
COPY gateway/src gateway/src

RUN mvn -pl gateway -am -DskipTests package

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/gateway/target/gateway-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

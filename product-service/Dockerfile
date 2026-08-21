FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY pom.xml .
COPY gateway/pom.xml gateway/pom.xml
COPY api-server/pom.xml api-server/pom.xml
COPY product-service/pom.xml product-service/pom.xml
COPY pricing-service/pom.xml pricing-service/pom.xml
COPY product-service/src product-service/src

RUN mvn -pl product-service -am -DskipTests package

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/product-service/target/product-service-*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]

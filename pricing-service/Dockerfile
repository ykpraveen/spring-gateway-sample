FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
COPY gateway/pom.xml gateway/pom.xml
COPY api-server/pom.xml api-server/pom.xml
COPY product-service/pom.xml product-service/pom.xml
COPY pricing-service/pom.xml pricing-service/pom.xml
COPY pricing-service/src pricing-service/src

RUN ./mvnw -pl pricing-service -am -DskipTests package

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/pricing-service/target/pricing-service-*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]

# dev stage — Maven + JDK, runs with spring-boot:run (used by docker-compose.yml)
FROM maven:3.9-eclipse-temurin-21 AS dev
WORKDIR /app
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw \
    && mvn dependency:go-offline -q \
    && mvn dependency:resolve-plugins -q \
    && mvn -q -DskipTests test-compile
COPY src ./src
EXPOSE 8080
CMD ["./mvnw", "spring-boot:run"]

# build stage — compiles the fat JAR
FROM dev AS build
RUN mvn -q -DskipTests package

# runtime stage — slim JRE, runs the pre-built JAR (use for production)
FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache postgresql-client openssl bash
COPY --from=build /app/target/*.jar app.jar
COPY entrypoint.sh /app/
RUN chmod +x /app/entrypoint.sh
VOLUME ["/var/meridian/backups"]
ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["java", "-jar", "app.jar"]

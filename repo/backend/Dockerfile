FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
# -Dmaven.test.skip=true skips both test compilation and execution; the runtime
# image does not need the test sources (tests are run separately via run_tests.sh).
RUN mvn package -Dmaven.test.skip=true -q

# amazoncorretto:17-alpine is multi-arch (linux/amd64 + linux/arm64); the
# eclipse-temurin alpine JRE only ships amd64, which breaks builds on arm64 hosts.
FROM amazoncorretto:17-alpine
WORKDIR /app
RUN apk add --no-cache postgresql-client openssl bash wget
COPY --from=build /app/target/*.jar app.jar
COPY entrypoint.sh /app/
RUN chmod +x /app/entrypoint.sh
VOLUME ["/var/meridian/backups"]
EXPOSE 3000
ENTRYPOINT ["/app/entrypoint.sh"]
CMD ["java", "-jar", "app.jar"]

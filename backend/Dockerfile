FROM eclipse-temurin:21.0.12_8-jdk-jammy@sha256:c7d5863b5dd8f26b90c64f1d80cc2b0e5a5e4642f8db9955a370d348edd8f438 AS build
WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn ./.mvn
RUN sh mvnw -B -ntp dependency:go-offline
COPY src ./src
RUN sh mvnw -B -ntp verify

FROM eclipse-temurin:21.0.12_8-jre-jammy@sha256:61d6c7b34d36aee3f45d043101259f97f3c6d428dc2a6f75513789983c5e254f
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/target/daily-career-0.0.1-SNAPSHOT.jar app.jar
COPY --from=build /workspace/target/classes/dev/dailycareer/infrastructure/HealthProbe.class /app/probe/dev/dailycareer/infrastructure/HealthProbe.class
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

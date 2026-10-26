# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /workspace

# Resolve dependencies before copying sources to retain the Maven cache.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -DskipTests package \
    && cp target/brasil-0.0.1-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:25-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
COPY --from=build --chown=10001:10001 /workspace/app.jar ./app.jar
ENV SPRING_PROFILES_ACTIVE=prod
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

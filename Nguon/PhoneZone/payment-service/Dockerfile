FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -Dmaven.wagon.http.retryHandler.count=10 -Dmaven.resolver.transport.http.retryHandler.count=10 package -DskipTests -B

FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 1001 quarkus && \
    useradd  --system --uid 1001 --gid 1001 --no-create-home quarkus
WORKDIR /app
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/lib/      ./lib/
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/*.jar     ./
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/app/      ./app/
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/quarkus/  ./quarkus/
USER 1001
EXPOSE 8084
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]

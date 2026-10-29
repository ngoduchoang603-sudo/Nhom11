# ===========================================================================
# Dockerfile cho user-service (Quarkus JVM mode)
# Multi-stage build: Stage 1 build, Stage 2 chi giu runtime nho gon
# ===========================================================================

# ---------- Stage 1: BUILD ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy pom.xml truoc de cache dependency
COPY pom.xml .

# Copy source va build
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -Dmaven.wagon.http.retryHandler.count=10 -Dmaven.resolver.transport.http.retryHandler.count=10 package -DskipTests -B

# ---------- Stage 2: RUNTIME ----------
# Dung Eclipse Temurin JRE 21 (nhe hon JDK)
FROM eclipse-temurin:21-jre

# Tao user non-root de chay (bao mat)
RUN groupadd --system --gid 1001 quarkus && \
    useradd  --system --uid 1001 --gid 1001 --no-create-home quarkus

WORKDIR /app

# Quarkus fast-jar layout: copy 4 thu muc theo dung thu tu
# (lib khong doi thuong xuyen, app code thay doi nhieu nhat — toi uu cache layer)
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/lib/      ./lib/
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/*.jar     ./
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/app/      ./app/
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/quarkus/  ./quarkus/

USER 1001
EXPOSE 8081

# JAVA_OPTS_APPEND: cho phep them option khi chay container
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]

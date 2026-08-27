# Layered Spring Boot image: dependencies, loader, snapshots, and application code are separate
# layers, so a code change only rebuilds the last one. Runs as an unprivileged user.
FROM eclipse-temurin:21.0.12_8-jre@sha256:7a65df4b22d2de92d4e04056e884f3b9122d70b21e2847fd66084278bd0ce037 AS extract
WORKDIR /build
COPY target/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:21.0.12_8-jre@sha256:7a65df4b22d2de92d4e04056e884f3b9122d70b21e2847fd66084278bd0ce037
RUN useradd --system --uid 10001 --user-group --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=extract --chown=app:app /build/extracted/dependencies/ ./
COPY --from=extract --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=app:app /build/extracted/application/ ./
USER 10001
# Size the heap from the container limit; Kubernetes sets the same value explicitly.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "application.jar"]

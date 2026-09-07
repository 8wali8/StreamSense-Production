FROM eclipse-temurin:21.0.12_8-jre@sha256:7a65df4b22d2de92d4e04056e884f3b9122d70b21e2847fd66084278bd0ce037

WORKDIR /app

COPY target/*jar app.jar

EXPOSE 8085

ENTRYPOINT ["java","-jar","/app/app.jar"]

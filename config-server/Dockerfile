# base image that we start from, temurin is widely used dist.
FROM eclipse-temurin:21.0.12_8-jre@sha256:7a65df4b22d2de92d4e04056e884f3b9122d70b21e2847fd66084278bd0ce037

# sets working dir inside container
WORKDIR /app

# copies JAR built from machine into container as /app/app.jar
COPY target/*jar app.jar

# documents that container listens on port 8888
EXPOSE 8888

# sets command that runs when container starts (java -jar /app/app.jar)
ENTRYPOINT ["java","-jar","/app/app.jar"]
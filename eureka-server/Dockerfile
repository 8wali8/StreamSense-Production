# base image that we start from, temurin is widely used dist.
FROM eclipse-temurin:21-jre

# sets working dir inside container
WORKDIR /app

# copies JAR built from machine into container as /app/app.jar
COPY target/*jar app.jar

# documents that container listens on port 8761
EXPOSE 8761

# sets command that runs when container starts (java -jar /app/app.jar)
ENTRYPOINT ["java","-jar","/app/app.jar"]


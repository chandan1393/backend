FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copy only jar (faster builds)
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java","-jar","app.jar"]
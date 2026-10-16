FROM node:22-slim AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend ./
RUN npm run build
FROM maven:3.9.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY --from=frontend /app/src/main/resources/static/app ./src/main/resources/static/app
RUN mvn -B package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/target/trustflow-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

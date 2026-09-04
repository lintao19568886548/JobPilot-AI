FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/backend

COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline

COPY backend/src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 jobpilot \
    && adduser -S -D -H -u 10001 -G jobpilot jobpilot
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/backend/target/jobpilot-backend-0.1.0-SNAPSHOT.jar /app/jobpilot-backend.jar

USER 10001:10001
EXPOSE 8088
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.io.tmpdir=/tmp", "-jar", "/app/jobpilot-backend.jar"]

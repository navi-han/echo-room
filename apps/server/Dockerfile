FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace/apps/server
COPY apps/server/ ./
RUN chmod +x ./gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/apps/server/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Build the jar
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Dependencies in their own layer, so changing code does not download them again
RUN ./mvnw -B -q dependency:go-offline
COPY src src
# Tests run in CI; the image only packages
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
# The code runner starts sandbox containers through the host's Docker daemon (its socket is
# mounted in docker-compose.yml), so this image needs the docker command-line client only
COPY --from=docker:28-cli /usr/local/bin/docker /usr/local/bin/docker
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

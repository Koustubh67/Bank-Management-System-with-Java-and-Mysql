# ---- Build stage: compile and package the app ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

# ---- Run stage: small image with only the JRE and the jar ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system bank
COPY --from=build /app/target/bank-0.0.1-SNAPSHOT.jar app.jar
USER bank
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

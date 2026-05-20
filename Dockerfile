FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*

COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/target/timetable-bot-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

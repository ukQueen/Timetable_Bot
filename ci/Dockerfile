FROM maven:3.9.10-eclipse-temurin-21

WORKDIR /app

# copy pom first for better layer caching
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline || true

COPY src ./src

CMD ["mvn", "-B", "-ntp", "test"]

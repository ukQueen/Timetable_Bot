FROM eclipse-temurin:25-jdk

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends maven \
    && rm -rf /var/lib/apt/lists/*

# copy pom first for better layer caching
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline || true

COPY src ./src

CMD ["mvn", "-B", "-ntp", "test"]

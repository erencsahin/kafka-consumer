#kafka-consumer-postgresql/Dockerfile
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# monorepo root'u komple kopyala
COPY . .

# "kafka-consumer-postgresql" modülünü ve ona bağlı tüm bağımlılıkları derle
RUN mvn clean package -pl kafka-consumer-postgresql -am -DskipTests -q

FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

# sadece kafka-consumer-postgresql modülünün jar'ını çek
COPY --from=builder /build/kafka-consumer-postgresql/target/kafka-consumer-postgresql-*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]

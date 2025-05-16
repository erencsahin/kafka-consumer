FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 1. Parent POM'u kopyala
COPY pom.xml .

# 2. Tüm modül dizinlerini kopyala
COPY rest/pom.xml ./rest/
COPY kafka-consumer/pom.xml ./kafka-consumer/
COPY tcp/pom.xml ./tcp/
COPY coordinator/pom.xml ./coordinator/

# 3. Sadece ilgili modülün kaynak kodunu kopyala
COPY kafka-consumer/src ./kafka-consumer/src

RUN mvn clean package -pl kafka-consumer -am -DskipTests -q

FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
COPY --from=builder /app/kafka-consumer/target/rest-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","app.jar"]
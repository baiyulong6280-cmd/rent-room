# syntax=docker/dockerfile:1.7

FROM maven:3.9.9-eclipse-temurin-8 AS builder

WORKDIR /workspace
COPY . .

RUN --mount=type=cache,id=m2-cache,target=/root/.m2 \
    mvn -T 1C -pl yudao-server -am package -DskipTests

FROM eclipse-temurin:8-jre

WORKDIR /app
COPY --from=builder /workspace/yudao-server/target/yudao-server.jar app.jar

ENV TZ=Asia/Shanghai
ENV SPRING_PROFILES_ACTIVE=railway
ENV JAVA_OPTS="-Xms512m -Xmx512m -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-railway}"]

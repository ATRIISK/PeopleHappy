# 众乐电子商城 - 后端 Dockerfile
# 多阶段构建

# === Stage 1: 编译 ===
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml .
# 先下载依赖（利用 Docker 缓存层）
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# === Stage 2: 运行 ===
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8074
ENTRYPOINT ["java", "-jar", "app.jar"]
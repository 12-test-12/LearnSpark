# =============================================================================
# LearnSpark 后端 Dockerfile（多阶段构建）
# 构建：docker build -t learnspark-backend:0.1.0 .
# =============================================================================

# ---------- Stage 1: 构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先拷贝 pom，利用缓存加速依赖下载
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 拷贝源码并打包（跳过测试）
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------- Stage 2: 运行 ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 时区
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m -Duser.timezone=Asia/Shanghai"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

HEALTHCHECK --interval=30s --timeout=10s --retries=5 \
  CMD curl -f http://localhost:8080/api/v1/actuator/health || exit 1

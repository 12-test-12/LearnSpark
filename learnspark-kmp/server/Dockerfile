# 阶段 3.2：LearnSpark 后端 Dockerfile（多阶段）
# 阶段一：Gradle 构建（用 Tencent Cloud 镜像提速）
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 阿里云镜像加速
ENV GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx2g -Dorg.gradle.daemon=false"
ENV GRADLE_USER_HOME=/root/.gradle

# 先复制构建文件，利用 Docker 缓存
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY server/build.gradle.kts server/build.gradle.kts
COPY composeApp/ composeApp/

# 复制 server 源码
COPY server/ server/

# 构建 server bootJar（跳过测试）
RUN ./gradlew :server:bootJar -x test --no-daemon --quiet

# 阶段二：运行时镜像（仅 JRE + 产物）
FROM eclipse-temurin:17-jre
WORKDIR /app

# 非 root 用户运行（安全）
RUN groupadd -r learnspark && useradd -r -g learnspark learnspark

# 中文环境 + UTF-8
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8
ENV TZ=Asia/Shanghai

# 上传目录（挂载点）
RUN mkdir -p /app/uploads /app/logs && chown -R learnspark:learnspark /app

# 复制构建产物
COPY --from=build /app/server/build/libs/server-1.0.0.jar /app/server.jar

USER learnspark

# 启动参数（profile 通过环境变量或命令行覆盖）
ENV JAVA_OPTS="-Xmx512m -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=api
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/server.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]

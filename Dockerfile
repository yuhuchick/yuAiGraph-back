# ── 构建阶段 ──────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# 先只复制 pom.xml，利用 Docker 层缓存下载依赖
COPY pom.xml .
RUN mvn dependency:go-offline -q

# 再复制源码并编译
COPY src ./src
RUN mvn package -DskipTests -q

# ── 运行阶段 ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 安装 netcat，用于等待 MySQL 就绪
RUN apk add --no-cache netcat-openbsd

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# 等待 MySQL 端口开放后再启动 Java（MYSQLHOST/MYSQLPORT 由 Railway 自动注入）
ENTRYPOINT ["sh", "-c", "\
  echo 'Waiting for MySQL...' && \
  until nc -z -w3 ${MYSQLHOST:-mysql.railway.internal} ${MYSQLPORT:-3306} 2>/dev/null; do \
    echo 'MySQL not ready, retry in 3s...'; \
    sleep 3; \
  done && \
  echo 'MySQL is ready, starting app...' && \
  exec java \
    -Xms128m -Xmx350m \
    -XX:MaxMetaspaceSize=128m \
    -XX:+UseContainerSupport \
    -XX:+UseG1GC \
    -jar app.jar"]

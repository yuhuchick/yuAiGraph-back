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

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Xms128m", "-Xmx350m", \
  "-XX:MaxMetaspaceSize=128m", \
  "-XX:+UseContainerSupport", \
  "-XX:+UseG1GC", \
  "-jar", "app.jar"]

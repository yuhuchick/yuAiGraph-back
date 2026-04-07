# AI 知识图谱 · 后端（Spring Boot）

为「AI 知识图谱笔记」前端提供 REST API：用户认证（JWT）、笔记与图谱、文档解析任务、AI 对话（SSE）、分享链接等。

## 要求

- **JDK 21**
- **Maven 3.6+**
- **MySQL 8**（或兼容版本）

## 配置

主配置：`src/main/resources/application.yml`。

支持通过环境变量覆盖（生产部署推荐）：

| 变量 | 说明 |
|------|------|
| `PORT` | 监听端口（如云主机 `8080`；部分平台会注入 `PORT`） |
| `DB_URL` | JDBC URL，须带引号写入 systemd `EnvironmentFile`（含 `&` 时） |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 |
| `JWT_SECRET` | JWT 签名密钥（足够长随机串） |
| `APP_BASE_URL` | 前端站点根 URL，用于生成分享链接，如 `https://example.com` |
| `AI_API_KEY` / `AI_CHAT_MODEL` | 上游 AI 服务 |

## 本地运行

```bash
mvn spring-boot:run
```

默认 <http://localhost:8080>，API 前缀为 `/api/v1/...`。请本地先准备好 MySQL 库表（`ddl-auto: update` 时可自动建表，视配置而定）。

## 构建与运行 JAR

```bash
mvn package -DskipTests
java -jar target/ai-back-0.0.1-SNAPSHOT.jar
```

## Docker（可选）

仓库根目录提供 `Dockerfile`，可用于容器化部署；`PORT` 由运行环境注入。

## 部署脚本

- `deploy.sh`：在 Linux 服务器上拉取代码、Maven 打包、systemd 托管。使用前配置 `/opt/deploy/deploy.env`（见脚本内说明）。

## 健康检查

- `GET /`、`GET /healthz`：返回 JSON `{"code":0,"message":"ok","data":{"status":"ok"}}`（供负载均衡或探活使用）。

## 相关项目

- 前端（Next.js）：独立仓库，通过 `JAVA_API_BASE` 将 Next 的 `/api/*` 代理到本服务。

#!/bin/bash
# 后端部署脚本 - 在服务器上执行
# 用法: bash deploy.sh
set -e

ENV_FILE="/opt/deploy/deploy.env"
APP_DIR="/opt/yuAiGraph-back"
LOG_DIR="/var/log/yuAiGraph-back"

# ── 检查配置文件 ───────────────────────────────────────────────
if [ ! -f "$ENV_FILE" ]; then
    echo "❌ 未找到配置文件 $ENV_FILE，请先创建："
    cat << 'EXAMPLE'
cat > /opt/deploy/deploy.env << EOF
DB_URL=jdbc:mysql://localhost:3306/knowledge_graph?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=UTF-8
DB_USERNAME=kg_user
DB_PASSWORD=你的数据库密码
JWT_SECRET=$(openssl rand -hex 32)
APP_BASE_URL=https://yudev.top
AI_API_KEY=你的AI密钥
AI_CHAT_MODEL=MiniMax-M2.5
GIT_REPO_URL=https://你的仓库/ai-back.git
# GIT_BRANCH=main
EOF
EXAMPLE
    exit 1
fi
source "$ENV_FILE"

# 代码目录与分支（在 deploy.env 里可设 REPO_DIR、GIT_BRANCH、GIT_REPO_URL）
REPO_DIR="${REPO_DIR:-$APP_DIR/repo}"
GIT_BRANCH="${GIT_BRANCH:-main}"

# 接口访问日志目录（Tomcat access log），默认同应用日志目录
ACCESS_LOG_DIR="${ACCESS_LOG_DIR:-$LOG_DIR}"

mkdir -p "$APP_DIR" "$LOG_DIR" "$ACCESS_LOG_DIR"

# ── 拉取/更新代码 ──────────────────────────────────────────────
echo "【后端】同步代码 → $REPO_DIR （分支: $GIT_BRANCH）"
if [ -d "$REPO_DIR/.git" ]; then
    git -C "$REPO_DIR" fetch origin --prune
    git -C "$REPO_DIR" checkout "$GIT_BRANCH"
    # 与远端完全一致，避免服务器残留合并/本地提交导致“永远不是最新”
    git -C "$REPO_DIR" reset --hard "origin/$GIT_BRANCH"
    echo "【后端】当前提交: $(git -C "$REPO_DIR" log -1 --oneline)"
elif [ "${USE_LOCAL_REPO:-0}" = "1" ]; then
    # 仅本机调试：在仓库根目录执行 bash deploy.sh，且 deploy.env 设 USE_LOCAL_REPO=1
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    if [ ! -f "$SCRIPT_DIR/pom.xml" ]; then
        echo "❌ USE_LOCAL_REPO=1 时请在含 pom.xml 的仓库根目录执行 deploy.sh"
        exit 1
    fi
    REPO_DIR="$SCRIPT_DIR"
    echo "【后端】使用本地仓库（不拉取远程）: $REPO_DIR"
    echo "【后端】当前提交: $(git -C "$REPO_DIR" log -1 --oneline 2>/dev/null || echo '非 git 目录')"
else
    if [ -z "${GIT_REPO_URL:-}" ]; then
        echo "❌ 目录 $REPO_DIR 不是 git 仓库，且未配置 GIT_REPO_URL。"
        echo "   请任选其一："
        echo "   1) 在 $ENV_FILE 中设置 GIT_REPO_URL=... ，重新执行本脚本（将自动 clone）；"
        echo "   2) 手动执行: git clone -b $GIT_BRANCH <url> $REPO_DIR"
        echo "   3) 本机联调: USE_LOCAL_REPO=1 且在仓库根目录执行"
        exit 1
    fi
    mkdir -p "$(dirname "$REPO_DIR")"
    if [ -d "$REPO_DIR" ] && [ ! -d "$REPO_DIR/.git" ]; then
        echo "❌ $REPO_DIR 已存在但不是 git 仓库，请备份后删除该目录再部署"
        exit 1
    fi
    # 全量 clone，避免 shallow 在部分环境下 fetch/reset 异常
    git clone -b "$GIT_BRANCH" "$GIT_REPO_URL" "$REPO_DIR"
    echo "【后端】已克隆，当前提交: $(git -C "$REPO_DIR" log -1 --oneline)"
fi

# ── Maven 打包 ─────────────────────────────────────────────────
echo "【后端】Maven 打包中..."
cd "$REPO_DIR"
mvn package -DskipTests -q
cp target/*.jar "$APP_DIR/app.jar"
echo "【后端】打包完成：$(ls -lh $APP_DIR/app.jar | awk '{print $5}')"

# ── 写入环境变量 ───────────────────────────────────────────────
cat > "$APP_DIR/.env" << BACKENV
DB_URL="${DB_URL}"
DB_USERNAME="${DB_USERNAME}"
DB_PASSWORD="${DB_PASSWORD}"
JWT_SECRET="${JWT_SECRET}"
APP_BASE_URL="${APP_BASE_URL}"
AI_API_KEY="${AI_API_KEY}"
AI_CHAT_MODEL="${AI_CHAT_MODEL}"
ACCESS_LOG_DIR="${ACCESS_LOG_DIR}"
BACKENV

# ── 注册 systemd 服务 ──────────────────────────────────────────
cat > /etc/systemd/system/yuAiGraph-back.service << SVCEOF
[Unit]
Description=AI Knowledge Graph Backend
After=network.target mysqld.service mariadb.service

[Service]
Type=simple
WorkingDirectory=${APP_DIR}
EnvironmentFile=${APP_DIR}/.env
ExecStart=java -Xms128m -Xmx400m -XX:+UseG1GC -jar ${APP_DIR}/app.jar
Restart=on-failure
RestartSec=10
StandardOutput=append:${LOG_DIR}/app.log
StandardError=append:${LOG_DIR}/app.log

[Install]
WantedBy=multi-user.target
SVCEOF

# ── 重启服务 ───────────────────────────────────────────────────
systemctl daemon-reload
systemctl enable yuAiGraph-back
systemctl restart yuAiGraph-back

echo "【后端】等待启动..."
sleep 6
if systemctl is-active --quiet yuAiGraph-back; then
    echo "【后端】✅ 启动成功"
    echo "        应用日志: tail -f $LOG_DIR/app.log"
    echo "        接口日志: tail -f $ACCESS_LOG_DIR/access*.log"
else
    echo "【后端】❌ 启动失败，最近日志："
    journalctl -u yuAiGraph-back -n 30 --no-pager
    exit 1
fi

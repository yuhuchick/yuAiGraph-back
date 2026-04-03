#!/bin/bash
# 后端部署脚本 - 在服务器上执行
# 用法: bash deploy.sh
set -e

ENV_FILE="/opt/deploy/deploy.env"
APP_DIR="/opt/ai-back"
LOG_DIR="/var/log/ai-back"
REPO_DIR="$APP_DIR/repo"

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
EOF
EXAMPLE
    exit 1
fi
source "$ENV_FILE"

mkdir -p "$APP_DIR" "$LOG_DIR"

# ── 拉取/更新代码 ──────────────────────────────────────────────
echo "【后端】拉取最新代码..."
if [ -d "$REPO_DIR/.git" ]; then
    git -C "$REPO_DIR" pull
else
    # 首次部署：把当前目录作为源（脚本在仓库内运行）
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    if [ -f "$SCRIPT_DIR/pom.xml" ]; then
        REPO_DIR="$SCRIPT_DIR"
    else
        echo "❌ 找不到 pom.xml，请在仓库根目录执行此脚本"
        exit 1
    fi
fi

# ── Maven 打包 ─────────────────────────────────────────────────
echo "【后端】Maven 打包中..."
cd "$REPO_DIR"
mvn package -DskipTests -q
cp target/*.jar "$APP_DIR/app.jar"
echo "【后端】打包完成：$(ls -lh $APP_DIR/app.jar | awk '{print $5}')"

# ── 写入环境变量 ───────────────────────────────────────────────
cat > "$APP_DIR/.env" << BACKENV
DB_URL=${DB_URL}
DB_USERNAME=${DB_USERNAME}
DB_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}
APP_BASE_URL=${APP_BASE_URL}
AI_API_KEY=${AI_API_KEY}
AI_CHAT_MODEL=${AI_CHAT_MODEL}
BACKENV

# ── 注册 systemd 服务 ──────────────────────────────────────────
cat > /etc/systemd/system/ai-back.service << SVCEOF
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
systemctl enable ai-back
systemctl restart ai-back

echo "【后端】等待启动..."
sleep 6
if systemctl is-active --quiet ai-back; then
    echo "【后端】✅ 启动成功"
    echo "        日志: tail -f $LOG_DIR/app.log"
else
    echo "【后端】❌ 启动失败，最近日志："
    journalctl -u ai-back -n 30 --no-pager
    exit 1
fi

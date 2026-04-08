#!/bin/bash
# 后端部署脚本 - 在服务器上执行
# 用法: 在服务器执行 bash /opt/yuAiGraph-back/deploy.sh（路径按你实际 APP_DIR）
#
# 支持两种常见目录：
#   A) 源码与 app.jar、deploy.sh 同在 APP_DIR（你当前这种：/opt/yuAiGraph-back 下有 pom.xml、src）
#   B) 源码在 APP_DIR/repo，运行目录仍是 APP_DIR
# 可选在 deploy.env 中设置 GIT_ROOT / MAVEN_DIR 强制指定。
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
# GIT_ROOT=/opt/yuAiGraph-back/repo
# MAVEN_DIR=/opt/yuAiGraph-back/repo/yuAiGraph-back
EOF
EXAMPLE
    exit 1
fi
source "$ENV_FILE"

# 接口访问日志目录（Tomcat access log），默认同应用日志目录
ACCESS_LOG_DIR="${ACCESS_LOG_DIR:-$LOG_DIR}"

mkdir -p "$APP_DIR" "$LOG_DIR" "$ACCESS_LOG_DIR"

# ── 确定 GIT 根目录与 Maven 工程目录 ───────────────────────────
GIT_ROOT="${GIT_ROOT:-}"
MAVEN_DIR="${MAVEN_DIR:-}"

if [ -z "$GIT_ROOT" ]; then
    if [ -d "$APP_DIR/.git" ]; then
        GIT_ROOT="$APP_DIR"
    elif [ -d "$APP_DIR/repo/.git" ]; then
        GIT_ROOT="$APP_DIR/repo"
    fi
fi

if [ -z "$MAVEN_DIR" ]; then
    if [ -n "$GIT_ROOT" ] && [ -f "$GIT_ROOT/pom.xml" ]; then
        MAVEN_DIR="$GIT_ROOT"
    elif [ -n "$GIT_ROOT" ] && [ -f "$GIT_ROOT/yuAiGraph-back/pom.xml" ]; then
        MAVEN_DIR="$GIT_ROOT/yuAiGraph-back"
    elif [ -f "$APP_DIR/pom.xml" ]; then
        MAVEN_DIR="$APP_DIR"
    else
        SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        if [ -f "$SCRIPT_DIR/pom.xml" ]; then
            MAVEN_DIR="$SCRIPT_DIR"
            if [ -z "$GIT_ROOT" ]; then
                if [ -d "$SCRIPT_DIR/.git" ]; then
                    GIT_ROOT="$SCRIPT_DIR"
                elif [ -d "$SCRIPT_DIR/../.git" ]; then
                    GIT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
                fi
            fi
        else
            echo "❌ 找不到 pom.xml。请在 $APP_DIR 或 GIT_ROOT 下包含 Maven 工程，或在 $ENV_FILE 设置 MAVEN_DIR"
            exit 1
        fi
    fi
fi

# ── 拉取/更新代码 ──────────────────────────────────────────────
echo "【后端】拉取最新代码..."
if [ -n "$GIT_ROOT" ] && [ -d "$GIT_ROOT/.git" ]; then
    git -C "$GIT_ROOT" pull
    echo "【后端】Git 目录: $GIT_ROOT"
else
    echo "⚠️ 未检测到 .git（跳过 git pull），仅本地 mvn package"
fi
echo "【后端】Maven 目录: $MAVEN_DIR"

# ── Maven 打包 ─────────────────────────────────────────────────
echo "【后端】Maven 打包中..."
cd "$MAVEN_DIR"
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

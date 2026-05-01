#!/usr/bin/env bash
set -euo pipefail

# ======== 配置区（按需修改）========
APP_NAME="novel-agent"
JAR_NAME="novel-agent-1.0-SNAPSHOT.jar"
LOCAL_JAR_PATH="target/${JAR_NAME}"

REMOTE_USER="alice"
REMOTE_HOST="154.8.213.134"
REMOTE_DIR="/opt/qq-bot"
SCREEN_SESSION="agent"

# 数据库参数（云端固定 3306）
DB_URL="jdbc:mysql://127.0.0.1:3306/novel_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=10000&socketTimeout=30000"
DB_USER="root"
DB_PASS="My\$qlR00t#2026"

# 如需传 profile，可取消注释
# APP_ARGS="--spring.profiles.active=prod"
APP_ARGS=""

# 日志与进程文件
REMOTE_LOG="${REMOTE_DIR}/${APP_NAME}.log"
REMOTE_PID_FILE="${REMOTE_DIR}/${APP_NAME}.pid"

echo "==> [1/5] Maven 打包"
mvn clean package -DskipTests

if [[ ! -f "${LOCAL_JAR_PATH}" ]]; then
  echo "❌ 未找到打包产物: ${LOCAL_JAR_PATH}"
  exit 1
fi

echo "==> [2/5] 上传 JAR 到云端"
scp "${LOCAL_JAR_PATH}" "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/${JAR_NAME}.new"

echo "==> [3/5] 云端替换 JAR"
ssh "${REMOTE_USER}@${REMOTE_HOST}" "bash -lc '
  set -euo pipefail
  cd \"${REMOTE_DIR}\"
  if [[ -f \"${JAR_NAME}\" ]]; then
    cp \"${JAR_NAME}\" \"${JAR_NAME}.bak.$(date +%Y%m%d_%H%M%S)\"
  fi
  mv \"${JAR_NAME}.new\" \"${JAR_NAME}\"
'"

echo "==> [4/5] 停止旧进程"
ssh "${REMOTE_USER}@${REMOTE_HOST}" "bash -lc '
  set -euo pipefail
  cd \"${REMOTE_DIR}\"
  screen -list | rg \"\\.${SCREEN_SESSION}[[:space:]]\" >/dev/null 2>&1 || screen -dmS \"${SCREEN_SESSION}\"
  # 温和停机：向 screen 会话发送 Ctrl+C
  screen -S \"${SCREEN_SESSION}\" -X stuff $'\''\003'\''
  sleep 2
  if [[ -f \"${REMOTE_PID_FILE}\" ]]; then
    OLD_PID=\$(cat \"${REMOTE_PID_FILE}\" || true)
    if [[ -n \"\${OLD_PID}\" ]] && kill -0 \"\${OLD_PID}\" 2>/dev/null; then
      # 兜底：如果 Ctrl+C 后仍存活，再发 TERM
      kill \"\${OLD_PID}\" || true
      sleep 2
    fi
    rm -f \"${REMOTE_PID_FILE}\"
  fi
'"

echo "==> [5/5] 在 screen(${SCREEN_SESSION}) 启动新进程（强制 3306）"
ssh "${REMOTE_USER}@${REMOTE_HOST}" "bash -lc '
  set -euo pipefail
  cd \"${REMOTE_DIR}\"
  screen -list | rg \"\\.${SCREEN_SESSION}[[:space:]]\" >/dev/null 2>&1 || screen -dmS \"${SCREEN_SESSION}\"
  START_CMD=\"java -jar \\\"${JAR_NAME}\\\" \
    --spring.datasource.url=\"${DB_URL}\" \
    --spring.datasource.username=\"${DB_USER}\" \
    --spring.datasource.password=\"${DB_PASS}\" \
    ${APP_ARGS} \
    > \\\"${REMOTE_LOG}\\\" 2>&1\"
  screen -S \"${SCREEN_SESSION}\" -X stuff \"\${START_CMD}\n\"
  sleep 1
  NEW_PID=\$(pgrep -f \"${JAR_NAME}\" | head -n 1 || true)
  [[ -n \"\${NEW_PID}\" ]] && echo \"\${NEW_PID}\" > \"${REMOTE_PID_FILE}\"
  sleep 2
  echo \"PID: \$(cat \"${REMOTE_PID_FILE}\" 2>/dev/null || echo N/A)\"
  echo \"LOG: ${REMOTE_LOG}\"
  echo \"SCREEN: ${SCREEN_SESSION}\"
'"

echo "✅ 发布完成"
echo "可执行远程日志查看："
echo "ssh ${REMOTE_USER}@${REMOTE_HOST} \"tail -n 80 ${REMOTE_LOG}\""
echo "可进入 screen："
echo "ssh ${REMOTE_USER}@${REMOTE_HOST} \"screen -r ${SCREEN_SESSION}\""

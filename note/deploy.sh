#!/usr/bin/env bash
# 在项目根目录执行（与 pom.xml 同级）：
#   chmod +x note/deploy.sh && ./note/deploy.sh
#
# Windows：可用 Git Bash；或 deploy.ps1
#
# 说明：
# - 使用 nohup 启动，数据源密码通过同源 export 传给 Java（避开 screen stuff 截断/# 注释问题）。
# - 若仍报 Access denied，说明服务器 MySQL 的 root 密码与下方 DB_PASS 不一致，需在服务器上用 mysql -u root -p 对齐后改本地 DB_PASS。
set -euo pipefail

# ======== 配置区（按需修改）========
JAR_NAME="novel-agent-1.0-SNAPSHOT.jar"
LOCAL_JAR_PATH="target/${JAR_NAME}"

REMOTE_USER="alice"
REMOTE_HOST="154.8.213.134"
REMOTE_DIR="/opt/qq-bot"

DB_URL="jdbc:mysql://127.0.0.1:3306/novel_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=10000&socketTimeout=30000"
DB_USER="root"
DB_PASS="${DB_PASS:-}"

OPENAI_API_KEY="${OPENAI_API_KEY:-}"
OPENAI_BASE_URL="${OPENAI_BASE_URL:-https://api.meai.cloud}"

JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx768m}"
APP_NAME="novel-agent"
APP_ARGS="" # 例：--server.port=8080

REMOTE_LOG="${REMOTE_DIR}/${APP_NAME}.log"
REMOTE_PID_FILE="${REMOTE_DIR}/${APP_NAME}.pid"

_remote_exports() {
  printf 'export REMOTE_DIR=%q JAR_NAME=%q REMOTE_LOG=%q REMOTE_PID_FILE=%q DB_URL=%q DB_USER=%q DB_PASS=%q OPENAI_API_KEY=%q OPENAI_BASE_URL=%q JAVA_OPTS=%q APP_ARGS=%q\n' \
    "${REMOTE_DIR}" "${JAR_NAME}" "${REMOTE_LOG}" "${REMOTE_PID_FILE}" "${DB_URL}" "${DB_USER}" "${DB_PASS}" "${OPENAI_API_KEY}" "${OPENAI_BASE_URL}" "${JAVA_OPTS}" "${APP_ARGS:-}"
}

echo "==> [1/5] Maven 打包"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"
mvn clean package -DskipTests

if [[ ! -f "${LOCAL_JAR_PATH}" ]]; then
  echo "❌ 未找到打包产物: ${ROOT_DIR}/${LOCAL_JAR_PATH}"
  exit 1
fi

if [[ -z "${DB_PASS}" ]]; then
  echo "❌ 未设置 DB_PASS。请先在本机环境变量中提供数据库密码，再运行部署脚本。"
  echo "   例: export DB_PASS='your_password'"
  exit 1
fi

if [[ -z "${OPENAI_API_KEY}" ]]; then
  echo "❌ 未设置 OPENAI_API_KEY。请先在本机环境变量中提供密钥，再运行部署脚本。"
  echo "   例: export OPENAI_API_KEY='sk-xxx'"
  exit 1
fi

echo "==> [2/5] 上传 JAR"
scp "${LOCAL_JAR_PATH}" "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/${JAR_NAME}.new"

echo "==> [3/5] 服务器替换 JAR（带备份）"
ssh "${REMOTE_USER}@${REMOTE_HOST}" "$(_remote_exports); bash -s" <<'EOS'
set -euo pipefail
cd "${REMOTE_DIR}"
if [[ -f "${JAR_NAME}" ]]; then
  cp "${JAR_NAME}" "${JAR_NAME}.bak.$(date +%Y%m%d_%H%M%S)"
fi
mv "${JAR_NAME}.new" "${JAR_NAME}"
EOS

echo "==> [4/5] 停止旧进程（PID + 兜底 pkill）"
ssh "${REMOTE_USER}@${REMOTE_HOST}" "$(_remote_exports); bash -s" <<'EOS'
set -euo pipefail
cd "${REMOTE_DIR}"
if [[ -f "${REMOTE_PID_FILE}" ]]; then
  OLD_PID="$(cat "${REMOTE_PID_FILE}" || true)"
  if [[ -n "${OLD_PID}" ]] && kill -0 "${OLD_PID}" 2>/dev/null; then
    kill "${OLD_PID}" || true
  fi
  rm -f "${REMOTE_PID_FILE}"
fi
sleep 1
# 兜底：干掉仍占着该 JAR 的进程（避免多次部署残留）
while IFS= read -r pid; do
  [[ "${pid}" =~ ^[0-9]+$ ]] && kill "${pid}" 2>/dev/null || true
done < <(pgrep -f "${JAR_NAME}" || true)
sleep 2
EOS

echo "==> [5/5] nohup 启动（SPRING_DATASOURCE_PASSWORD 环境变量 + SPRING_PROFILES_ACTIVE 清空）"
ssh "${REMOTE_USER}@${REMOTE_HOST}" "$(_remote_exports); bash -s" <<'EOS'
set -euo pipefail
cd "${REMOTE_DIR}"
export SPRING_DATASOURCE_PASSWORD="${DB_PASS}"
export OPENAI_API_KEY="${OPENAI_API_KEY}"
export OPENAI_BASE_URL="${OPENAI_BASE_URL}"
nohup env SPRING_PROFILES_ACTIVE= \
  java ${JAVA_OPTS} -jar "${JAR_NAME}" \
  --spring.datasource.url="${DB_URL}" \
  --spring.datasource.username="${DB_USER}" \
  ${APP_ARGS} \
  > "${REMOTE_LOG}" 2>&1 &
echo $! > "${REMOTE_PID_FILE}"
sleep 3
NEW_PID="$(cat "${REMOTE_PID_FILE}" 2>/dev/null || true)"
if [[ -z "${NEW_PID}" ]] || ! kill -0 "${NEW_PID}" 2>/dev/null; then
  echo "❌ 进程未存活，请看日志末尾"
  tail -40 "${REMOTE_LOG}" || true
  exit 1
fi
echo "PID: ${NEW_PID}"
echo "LOG: ${REMOTE_LOG}"
EOS

echo "✅ 发布完成"
echo "查看日志: ssh ${REMOTE_USER}@${REMOTE_HOST} \"tail -n 100 ${REMOTE_LOG}\""
echo "查看进程: ssh ${REMOTE_USER}@${REMOTE_HOST} \"pgrep -af ${JAR_NAME}\""

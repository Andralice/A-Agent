#!/usr/bin/env bash
# 在项目根目录执行（与 pom.xml 同级）：
#   chmod +x note/deploy.sh && ./note/deploy.sh
#
# Windows：可用 Git Bash；或 deploy.ps1
#
# 说明：
# - nohup：数据源密码通过同源 export 传给远端 Java（见下方环境变量）。
# - systemd：先在服务器安装 novel-agent.service + novel-agent.env（见 note/*.example），再：
#     DEPLOY_MODE=systemd ./note/deploy.sh
# - 若仍报 Access denied，说明服务器 MySQL 密码与 DB_PASS（或 env 文件）不一致。
#
# 安全：本文件若填写了数据库/API 密钥，请勿 push 到公开仓库；聊天里一旦发过密钥建议尽快轮换。
set -euo pipefail

# ======== 配置区（按需修改）========
JAR_NAME="novel-agent-1.0-SNAPSHOT.jar"
LOCAL_JAR_PATH="target/${JAR_NAME}"

REMOTE_USER="alice"
REMOTE_HOST="154.8.213.134"
REMOTE_DIR="/opt/qq-bot"

# nohup 模式：库 URL/用户走命令行；密码通过 DB_PASS 传到远端。
# 默认密钥仅本机使用；环境变量 DB_PASS / OPENAI_API_KEY 仍可覆盖下列默认值。
_DEFAULT_DB_PASS='My$qlR00t#2026'
_DEFAULT_OPENAI_KEY='sk-WzQMkepAo1qJKub5IAzbVxTXPupYW7HVtBlxw4AlyhsRqYDk'
DB_URL="jdbc:mysql://127.0.0.1:3306/novel_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=10000&socketTimeout=30000"
DB_USER="root"
DB_PASS="${DB_PASS:-${_DEFAULT_DB_PASS}}"
OPENAI_API_KEY="${OPENAI_API_KEY:-${_DEFAULT_OPENAI_KEY}}"
unset _DEFAULT_DB_PASS _DEFAULT_OPENAI_KEY
OPENAI_BASE_URL="${OPENAI_BASE_URL:-https://api.meai.cloud}"

JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx768m}"
REMOTE_JAVA="${REMOTE_JAVA:-java}"

APP_NAME="novel-agent"
APP_ARGS="" # 例：--server.port=9090

# nohup | systemd（systemd 需服务器已配置 unit，且 SYSTEMD_UNIT 名称一致）
DEPLOY_MODE="${DEPLOY_MODE:-nohup}"
SYSTEMD_UNIT="${SYSTEMD_UNIT:-novel-agent.service}"
SYSTEMD_CTL="${SYSTEMD_CTL:-sudo systemctl}"

REMOTE_LOG="${REMOTE_DIR}/${APP_NAME}.log"
REMOTE_PID_FILE="${REMOTE_DIR}/${APP_NAME}.pid"

_remote_exports() {
  printf 'export REMOTE_DIR=%q JAR_NAME=%q REMOTE_LOG=%q REMOTE_PID_FILE=%q DB_URL=%q DB_USER=%q DB_PASS=%q OPENAI_API_KEY=%q OPENAI_BASE_URL=%q JAVA_OPTS=%q REMOTE_JAVA=%q APP_ARGS=%q DEPLOY_MODE=%q SYSTEMD_UNIT=%q SYSTEMD_CTL=%q\n' \
    "${REMOTE_DIR}" "${JAR_NAME}" "${REMOTE_LOG}" "${REMOTE_PID_FILE}" "${DB_URL}" "${DB_USER}" "${DB_PASS}" "${OPENAI_API_KEY}" "${OPENAI_BASE_URL}" "${JAVA_OPTS}" "${REMOTE_JAVA}" "${APP_ARGS:-}" "${DEPLOY_MODE}" "${SYSTEMD_UNIT}" "${SYSTEMD_CTL}"
}

echo "==> [1/6] Maven 打包"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"
mvn clean package -DskipTests

if [[ ! -f "${LOCAL_JAR_PATH}" ]]; then
  echo "❌ 未找到打包产物: ${ROOT_DIR}/${LOCAL_JAR_PATH}"
  exit 1
fi

if [[ "${DEPLOY_MODE}" == "nohup" ]]; then
  if [[ -z "${DB_PASS}" ]]; then
    echo "❌ nohup 模式未设置 DB_PASS。请先 export DB_PASS='your_password'"
    exit 1
  fi
  if [[ -z "${OPENAI_API_KEY}" ]]; then
    echo "❌ nohup 模式未设置 OPENAI_API_KEY。请先 export OPENAI_API_KEY='sk-xxx'"
    exit 1
  fi
else
  echo "ℹ️  systemd 模式：请在服务器 ${REMOTE_DIR}/novel-agent.env 中配置数据库与 OPENAI_API_KEY（见 note/novel-agent.env.example）"
fi

REMOTE_DIR_Q="$(printf '%q' "${REMOTE_DIR}")"

echo "==> [2/6] 确保远程目录存在"
ssh "${REMOTE_USER}@${REMOTE_HOST}" "mkdir -p ${REMOTE_DIR_Q}"

echo "==> [3/6] 上传 JAR"
scp "${LOCAL_JAR_PATH}" "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/${JAR_NAME}.new"

echo "==> [4/6] 停止运行中的实例（先停再换包，避免占用旧 jar）"
ssh "${REMOTE_USER}@${REMOTE_HOST}" "$(_remote_exports); bash -s" <<'EOS'
set -euo pipefail
case "${DEPLOY_MODE}" in
  systemd)
    ${SYSTEMD_CTL} stop "${SYSTEMD_UNIT}" 2>/dev/null || true
    sleep 2
    ;;
  *)
    cd "${REMOTE_DIR}"
    if [[ -f "${REMOTE_PID_FILE}" ]]; then
      OLD_PID="$(cat "${REMOTE_PID_FILE}" || true)"
      if [[ -n "${OLD_PID}" ]] && kill -0 "${OLD_PID}" 2>/dev/null; then
        kill "${OLD_PID}" || true
      fi
      rm -f "${REMOTE_PID_FILE}"
    fi
    sleep 1
    while IFS= read -r pid; do
      [[ "${pid}" =~ ^[0-9]+$ ]] && kill "${pid}" 2>/dev/null || true
    done < <(pgrep -f "${JAR_NAME}" || true)
    sleep 2
    ;;
esac
EOS

echo "==> [5/6] 服务器替换 JAR（带备份）"
ssh "${REMOTE_USER}@${REMOTE_HOST}" "$(_remote_exports); bash -s" <<'EOS'
set -euo pipefail
cd "${REMOTE_DIR}"
if [[ -f "${JAR_NAME}" ]]; then
  cp "${JAR_NAME}" "${JAR_NAME}.bak.$(date +%Y%m%d_%H%M%S)"
fi
mv "${JAR_NAME}.new" "${JAR_NAME}"
EOS

echo "==> [6/6] 启动"
ssh "${REMOTE_USER}@${REMOTE_HOST}" "$(_remote_exports); bash -s" <<'EOS'
set -euo pipefail
case "${DEPLOY_MODE}" in
  systemd)
    ${SYSTEMD_CTL} daemon-reload 2>/dev/null || true
    ${SYSTEMD_CTL} start "${SYSTEMD_UNIT}"
    sleep 3
    if ! ${SYSTEMD_CTL} is-active --quiet "${SYSTEMD_UNIT}"; then
      echo "❌ ${SYSTEMD_UNIT} 未处于 active，最近日志："
      ${SYSTEMD_CTL} status "${SYSTEMD_UNIT}" --no-pager -l || true
      exit 1
    fi
    echo "OK: ${SYSTEMD_UNIT} is active"
    ;;
  *)
    cd "${REMOTE_DIR}"
    export SPRING_DATASOURCE_PASSWORD="${DB_PASS}"
    export OPENAI_API_KEY="${OPENAI_API_KEY}"
    export OPENAI_BASE_URL="${OPENAI_BASE_URL}"
    nohup env SPRING_PROFILES_ACTIVE= \
      "${REMOTE_JAVA}" ${JAVA_OPTS} -jar "${JAR_NAME}" \
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
    ;;
esac
EOS

echo "✅ 发布完成（模式: ${DEPLOY_MODE}）"
if [[ "${DEPLOY_MODE}" == "systemd" ]]; then
  echo "查看日志: ssh ${REMOTE_USER}@${REMOTE_HOST} \"sudo journalctl -u ${SYSTEMD_UNIT} -n 100 --no-pager\""
else
  echo "查看日志: ssh ${REMOTE_USER}@${REMOTE_HOST} \"tail -n 100 ${REMOTE_LOG}\""
fi
echo "查看进程: ssh ${REMOTE_USER}@${REMOTE_HOST} \"pgrep -af ${JAR_NAME}\""

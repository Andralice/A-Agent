#!/usr/bin/env bash
# 在仓库根目录执行 git add -A、commit（有变更时）、push。
# 用法（在仓库根）:
#   bash note/push-github.sh
#   bash note/push-github.sh "docs: 更新 API 总表"
# 仅提交不推送:
#   SKIP_PUSH=1 bash note/push-github.sh "wip: local only"

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if ! git rev-parse --git-dir >/dev/null 2>&1; then
  echo "error: not a git repository: $ROOT" >&2
  exit 1
fi

MSG="${1:-chore: sync $(date '+%Y-%m-%d %H:%M')}"

git add -A

if git diff --cached --quiet; then
  echo "Nothing to commit."
  if [[ "${SKIP_PUSH:-0}" != "1" ]]; then
    echo "Running git push (sync existing local commits)…"
    git push
  fi
  exit 0
fi

git commit -m "$MSG"

if [[ "${SKIP_PUSH:-0}" != "1" ]]; then
  git push
fi

echo "Done."

#!/bin/bash
set -euo pipefail

BASE_DIR=$(cd "$(dirname "$0")"; pwd)
RUN_DIR="$BASE_DIR/run"

stop_one() {
  local name="$1"
  local pidfile="$RUN_DIR/$name.pid"

  if [[ ! -f "$pidfile" ]]; then
    echo "[SKIP] $name not running (no pidfile)."
    return 0
  fi

  local pid
  pid=$(cat "$pidfile" || true)
  if [[ -z "${pid:-}" ]]; then
    echo "[WARN] $name pidfile empty, removing."
    rm -f "$pidfile"
    return 0
  fi

  if ! kill -0 "$pid" 2>/dev/null; then
    echo "[SKIP] $name not running (pid=$pid not exists). removing pidfile."
    rm -f "$pidfile"
    return 0
  fi

  echo "[STOP] $name (pid=$pid) ..."
  kill "$pid" 2>/dev/null || true

  # 等待最多 10 秒优雅退出
  for i in {1..10}; do
    if kill -0 "$pid" 2>/dev/null; then
      sleep 1
    else
      echo "[OK] $name stopped."
      rm -f "$pidfile"
      return 0
    fi
  done

  echo "[KILL] $name still running, force kill (pid=$pid)"
  kill -9 "$pid" 2>/dev/null || true
  rm -f "$pidfile"
  echo "[OK] $name killed."
}

echo "===== Stopping paypush-demo services ====="
stop_one "trans"
stop_one "push"
stop_one "order"
echo "===== Done ====="


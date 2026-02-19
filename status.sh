#!/bin/bash
set -euo pipefail

BASE_DIR=$(cd "$(dirname "$0")"; pwd)
RUN_DIR="$BASE_DIR/run"

status_one() {
  local name="$1"
  local pidfile="$RUN_DIR/$name.pid"

  if [[ ! -f "$pidfile" ]]; then
    echo "$name: STOPPED (no pidfile)"
    return 0
  fi

  local pid
  pid=$(cat "$pidfile" || true)
  if [[ -z "${pid:-}" ]]; then
    echo "$name: STOPPED (empty pidfile)"
    return 0
  fi

  if kill -0 "$pid" 2>/dev/null; then
    echo "$name: RUNNING (pid=$pid)"
  else
    echo "$name: STOPPED (stale pidfile pid=$pid)"
  fi
}

echo "===== paypush-demo status ====="
status_one "order"
status_one "push"
status_one "trans"


#!/bin/bash
set -euo pipefail

BASE_DIR=$(cd "$(dirname "$0")"; pwd)
RUN_DIR="$BASE_DIR/run"
mkdir -p "$RUN_DIR"

start_one() {
  local name="$1"
  local jar="$2"
  local log="$3"
  shift 3

  local pidfile="$RUN_DIR/$name.pid"

  if [[ -f "$pidfile" ]]; then
    local pid
    pid=$(cat "$pidfile" || true)
    if [[ -n "${pid:-}" ]] && kill -0 "$pid" 2>/dev/null; then
      echo "[SKIP] $name already running (pid=$pid). log=$log"
      return 0
    else
      rm -f "$pidfile"
    fi
  fi

  if [[ ! -f "$jar" ]]; then
    echo "[ERROR] jar not found: $jar"
    exit 1
  fi

  echo "[START] $name ..."
  nohup java -jar "$jar" "$@" > "$log" 2>&1 &
  local pid=$!
  echo "$pid" > "$pidfile"
  echo "[OK] $name started (pid=$pid). log=$log"
}

echo "===== Starting paypush-demo services ====="

start_one "order" "$BASE_DIR/order/target/order-1.0.0.jar" "$BASE_DIR/order.log"
sleep 1

# 你可以在这里给 push 增加模式，比如：--push.mode=blocking/offload/webclient
start_one "push" "$BASE_DIR/push/target/push-1.0.0.jar" "$BASE_DIR/push.log" --push.mode=blocking
# 或
#start_one "push" "$BASE_DIR/push/target/push-1.0.0.jar" "$BASE_DIR/push.log" --push.mode=offload
# 或
#start_one "push" "$BASE_DIR/push/target/push-1.0.0.jar" "$BASE_DIR/push.log" --push.mode=webclient

sleep 1

start_one "trans" "$BASE_DIR/trans/target/trans-1.0.0.jar" "$BASE_DIR/trans.log"

echo "===== Done. Use ./status.sh to check, tail -f *.log to view logs ====="


#!/usr/bin/env bash
set -euo pipefail

BASE_DIR=$(cd "$(dirname "$0")"; pwd)
RUN_DIR="$BASE_DIR/run"
mkdir -p "$RUN_DIR"

usage() {
  cat <<EOF
Usage:
  $(basename "$0") <mode>

Modes:
  blocking   --push.mode=blocking
  offload    --push.mode=offload
  webclient  --push.mode=webclient

Examples:
  $(basename "$0") blocking
  $(basename "$0") offload
  $(basename "$0") webclient
EOF
}

start_one() {
  local name="$1"
  local jar="$2"
  local log="$3"
  local config_file="$4"
  shift 4

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

  # 外部配置文件可选：存在就加载，不存在就只用 jar 内置配置
  local config_args=()
  if [[ -n "$config_file" && -f "$config_file" ]]; then
    # additional-location: 在不覆盖默认搜索路径的情况下，追加一个外部配置源
    config_args+=( "--spring.config.additional-location=file:${config_file}" )
    echo "[CONF] $name uses external config: $config_file"
  else
    echo "[CONF] $name external config not found, use jar config: $config_file"
  fi

  echo "[START] $name ..."
  nohup java -jar "$jar" "${config_args[@]}" "$@" > "$log" 2>&1 &
  local pid=$!
  echo "$pid" > "$pidfile"
  echo "[OK] $name started (pid=$pid). log=$log"
}

MODE="${1:-}"
if [[ -z "$MODE" ]]; then
  usage
  exit 1
fi

case "$MODE" in
  blocking|offload|webclient)
    ;;
  -h|--help|help)
    usage
    exit 0
    ;;
  *)
    echo "[ERROR] invalid mode: $MODE"
    usage
    exit 1
    ;;
esac

echo "===== Starting paypush-demo services (push.mode=$MODE) ====="

# 约定：配置文件放在脚本目录
ORDER_CFG="$BASE_DIR/order-application.yml"
PUSH_CFG="$BASE_DIR/push-application.yml"
TRANS_CFG="$BASE_DIR/trans-application.yml"

start_one "order" "$BASE_DIR/order/target/order-1.0.0.jar" "$BASE_DIR/order.log" "$ORDER_CFG"
sleep 1

start_one "push"  "$BASE_DIR/push/target/push-1.0.0.jar"  "$BASE_DIR/push.log"  "$PUSH_CFG"  --push.mode="$MODE"
sleep 1

start_one "trans" "$BASE_DIR/trans/target/trans-1.0.0.jar" "$BASE_DIR/trans.log" "$TRANS_CFG"

echo "===== Done. Use ./status.sh to check, tail -f *.log to view logs ====="

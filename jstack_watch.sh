#!/usr/bin/env bash
set -euo pipefail

PID="${1:-}"
N="${2:-5}"
INTERVAL="${3:-1}"

if [[ -z "$PID" ]]; then
  echo "Usage: $0 <pid> [times=5] [interval_seconds=1]"
  echo "./jstack_watch.sh <push进程PID> 5 1"
  exit 1
fi

OUTDIR="./jstack_dumps_$(date +%Y%m%d_%H%M%S)_pid${PID}"
mkdir -p "$OUTDIR"

echo "PID=$PID times=$N interval=${INTERVAL}s out=$OUTDIR"
echo "Tip: If jstack not found, run: which jstack (comes with JDK)."

for i in $(seq 1 "$N"); do
  f="$OUTDIR/jstack_${i}.txt"
  echo "==> dump $i/$N -> $f"
  jstack -l "$PID" > "$f" || true
  sleep "$INTERVAL"
done

echo
echo "================ Summary ================"

# 1) reactor-http threads count
reactor_total=$(grep -hE '^"reactor-http' "$OUTDIR"/jstack_*.txt | wc -l | tr -d ' ')
echo "[1] reactor-http threads (total occurrences across dumps): $reactor_total"

# 2) reactor-http threads blocked in socketRead0 / receiveResponseHeader
reactor_socketread=$(grep -hE '^"reactor-http' -n "$OUTDIR"/jstack_*.txt \
  | cut -d: -f1 | sort -u | wc -l | tr -d ' ')
socketread_hits=$(grep -hE 'java\.net\.SocketInputStream\.socketRead0|receiveResponseHeader|DefaultHttpResponseParser\.parseHead' "$OUTDIR"/jstack_*.txt | wc -l | tr -d ' ')
echo "[2] socketRead0/receiveResponseHeader related stack hits: $socketread_hits"

# 3) RestTemplate.exchange occurrences
rest_hits=$(grep -hE 'org\.springframework\.web\.client\.RestTemplate\.exchange' "$OUTDIR"/jstack_*.txt | wc -l | tr -d ' ')
echo "[3] RestTemplate.exchange hits (all threads): $rest_hits"

# 4) RestTemplate.exchange on reactor-http threads (rough detection)
#    We detect a block starting at reactor-http thread line until next thread header.
#    Simple awk: if within reactor thread block and see RestTemplate.exchange => count.
reactor_rest=$(awk '
  BEGIN{in=0; c=0}
  /^"reactor-http/ {in=1; next}
  /^"/ {in=0}
  { if(in && $0 ~ /org\.springframework\.web\.client\.RestTemplate\.exchange/) c++ }
  END{print c}
' "$OUTDIR"/jstack_*.txt)
echo "[4] RestTemplate.exchange inside reactor-http thread blocks (key signal): $reactor_rest"

# 5) boundedElastic threads doing RestTemplate.exchange (offload signal)
elastic_rest=$(awk '
  BEGIN{in=0; c=0}
  /^"boundedElastic/ {in=1; next}
  /^"/ {in=0}
  { if(in && $0 ~ /org\.springframework\.web\.client\.RestTemplate\.exchange/) c++ }
  END{print c}
' "$OUTDIR"/jstack_*.txt)
echo "[5] RestTemplate.exchange inside boundedElastic thread blocks (offload works): $elastic_rest"

echo
echo "=============== How to read ==============="
cat <<'EOF'
- If [4] > 0 : you are blocking Netty event-loop (bad) => matches your线上 jstack现象
- In offload mode: [4] should go to 0, and [5] should be > 0
- If socketRead hits are high while [4] > 0: often means downstream slow -> event-loop stuck waiting response
EOF

echo
echo "Dumps saved at: $OUTDIR"


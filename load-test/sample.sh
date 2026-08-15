#!/usr/bin/env bash
# 부하가 도는 동안 게이지 지표를 주기적으로 찍는다.
#
# pending·threads.busy·connections.active 는 게이지다. 그 순간의 값만 들고 있어서
# 실행이 끝난 뒤에 찍으면 언제나 0 이 나온다. "커넥션 대기가 없었다"가 아니라
# "지금은 없다"일 뿐이라, 측정이 끝난 뒤의 값으로 가설 3을 판정하면 반드시 틀린다.
#
# 사용법 — k6 를 돌리기 직전에 백그라운드로 띄우고, 끝나면 멈춘다.
#   ./load-test/sample.sh & SAMPLER=$!
#   k6 run load-test/scenarios/a-close-spike.js
#   kill $SAMPLER
set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
INTERVAL="${SAMPLE_INTERVAL:-0.5}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$HERE/results/raw"
mkdir -p "$OUT"

FILE="$OUT/$(date +%Y%m%d-%H%M%S)-samples.csv"

gauge() {
  curl -s --max-time 2 "${BASE_URL}/actuator/metrics/$1" \
    | sed -n 's/.*"statistic":"VALUE","value":\([0-9.E-]*\).*/\1/p' \
    | head -1
}

echo "epoch_ms,pending,active,idle,threads_busy,threads_current" > "$FILE"
echo "표본 수집 시작 — $FILE (${INTERVAL}초 간격)"

# INT/TERM 을 받으면 요약을 내고 끝낸다. kill 로 세우는 것이 정상 종료다.
summarize() {
  echo
  echo "=== 실행 중 최대값 ==="
  awk -F, 'NR > 1 {
      if ($2 + 0 > p) p = $2
      if ($3 + 0 > a) a = $3
      if ($5 + 0 > b) b = $5
      if ($6 + 0 > c) c = $6
      n++
  }
  END {
      printf "표본 %d개\n", n
      printf "hikaricp.connections.pending  최대 %s\n", p + 0
      printf "hikaricp.connections.active   최대 %s\n", a + 0
      printf "tomcat.threads.busy           최대 %s\n", b + 0
      printf "tomcat.threads.current        최대 %s\n", c + 0
  }' "$FILE"
  echo "표본 파일 — $FILE"
  exit 0
}

trap summarize INT TERM

while true; do
  printf '%s,%s,%s,%s,%s,%s\n' \
    "$(date +%s000)" \
    "$(gauge hikaricp.connections.pending)" \
    "$(gauge hikaricp.connections.active)" \
    "$(gauge hikaricp.connections.idle)" \
    "$(gauge tomcat.threads.busy)" \
    "$(gauge tomcat.threads.current)" >> "$FILE"

  sleep "$INTERVAL"
done

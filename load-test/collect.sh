#!/usr/bin/env bash
# 측정 전후 지표 스냅샷. 인자로 라벨을 준다 (before / after).
#
# 누적값을 쓰는 지표가 있어서 반드시 전후 두 번 찍어야 한다.
# Innodb_row_lock_time_avg 는 서버 기동 이후 전체 평균이라 한 번만 보면 의미가 없다.
set -uo pipefail

LABEL="${1:-snapshot}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-race-mysql}"
REDIS_CONTAINER="${REDIS_CONTAINER:-race-redis}"

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/guard.sh
source "$HERE/lib/guard.sh"

assert_local_load_profile "$BASE_URL" || exit 1
OUT="$HERE/results/raw"
mkdir -p "$OUT"

STAMP=$(date +%Y%m%d-%H%M%S)
FILE="$OUT/${STAMP}-${LABEL}.txt"

metric() {
  echo "--- $1"
  curl -s "${BASE_URL}/actuator/metrics/$1" || echo "(조회 실패)"
  echo
}

{
  echo "=== ${LABEL} / $(date '+%Y-%m-%d %H:%M:%S') ==="
  echo

  echo "=== 커넥션 풀 ==="
  metric hikaricp.connections.pending
  metric hikaricp.connections.active
  metric hikaricp.connections.usage
  metric hikaricp.connections.acquire
  metric hikaricp.connections.timeout

  echo "=== 톰캣 스레드 ==="
  # load 프로파일이 아니면 404 다. MBean 레지스트리가 꺼져 있으면 지표 자체가 없다.
  metric tomcat.threads.busy
  metric tomcat.threads.config.max

  echo "=== JVM ==="
  metric jvm.memory.used
  metric jvm.gc.pause
  metric jvm.gc.overhead

  echo "=== 스케줄러 풀 ==="
  # 계측 코드 없이 네 풀이 name 태그로 갈려 나온다.
  for pool in auctionProgressTaskScheduler roomStreamTaskScheduler \
              listStreamTaskScheduler notificationStreamTaskScheduler; do
    echo "--- executor.active name=${pool}"
    curl -s "${BASE_URL}/actuator/metrics/executor.active?tag=name:${pool}"
    echo
    echo "--- executor.queued name=${pool}"
    curl -s "${BASE_URL}/actuator/metrics/executor.queued?tag=name:${pool}"
    echo
  done

  echo "=== 요청 ==="
  metric http.server.requests

  echo "=== MySQL 행 잠금 (누적값 — 전후 차분을 본다) ==="
  docker exec "$MYSQL_CONTAINER" mysql -urace -prace race \
    -e "SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%';" 2>/dev/null || echo "(조회 실패)"

  echo
  echo "=== Redis 명령 호출 수 (인증 왕복 — 횟수만 본다) ==="
  # 로컬 Redis 는 왕복이 0에 가까워 소요 시간은 의미가 없다.
  # 요청 수 대비 get/ttl 호출 수가 몇 배인지가 이번에 얻으려는 값이다.
  docker exec "$REDIS_CONTAINER" redis-cli INFO commandstats 2>/dev/null \
    | grep -E 'cmdstat_(get|ttl|pttl|expire|setex|set|del)' || echo "(해당 명령 없음)"

  echo
  echo "=== 알림 적재량 (가설 1의 정황) ==="
  docker exec "$MYSQL_CONTAINER" mysql -urace -prace race \
    -e "select count(*) as notifications from notification;
        select count(*) as bids from bid where auction_id >= 9000;" 2>/dev/null || echo "(조회 실패)"

} | tee "$FILE"

echo
echo "저장됨 — $FILE"

if [ "$LABEL" = "before" ]; then
  echo
  echo "Redis 카운터를 초기화한다. 이 시점 이후의 호출만 세기 위해서다."
  docker exec "$REDIS_CONTAINER" redis-cli CONFIG RESETSTAT >/dev/null 2>&1 \
    && echo "  RESETSTAT 완료" || echo "  RESETSTAT 실패"
fi

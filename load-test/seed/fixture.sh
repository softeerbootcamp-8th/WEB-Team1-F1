#!/usr/bin/env bash
# 측정용 경매 데이터를 넣는다. 회차마다 다시 돌린다 —
# 경매 시각을 실행 시점 기준으로 다시 계산하기 때문이다.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/guard.sh
source "$HERE/../lib/guard.sh"

BASE_URL="${BASE_URL:-http://localhost:8080}"
CONTAINER="${MYSQL_CONTAINER:-race-mysql}"
DB_USER="${DB_USERNAME:-race}"
DB_PASS="${DB_PASSWORD:-race}"
DB_NAME="${DB_NAME:-race}"
SELLER_USERNAME="${SELLER_USERNAME:-load_seller}"

assert_local_load_profile "$BASE_URL"

mysql_exec() {
  docker exec -i "$CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" "$@" 2>/dev/null
}

# 판매자 계정이 없으면 아래 insert 가 seller_id 제약 위반으로 죽고 원인이 안 보인다.
# 먼저 확인해서 무엇을 해야 하는지 알려준다.
seller_id=$(mysql_exec -N -e "select id from users where username = '${SELLER_USERNAME}';")

if [ -z "$seller_id" ]; then
  echo "중단 — ${SELLER_USERNAME} 계정이 없다." >&2
  echo "  ./load-test/seed/accounts.sh 를 먼저 돌린다." >&2
  exit 1
fi

echo "판매자 확인 — ${SELLER_USERNAME} (user id ${seller_id})"

mysql_exec < "$HERE/fixture.sql"

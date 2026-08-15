#!/usr/bin/env bash
# 시나리오 B용 — 9100번대 경매의 마감 시각을 "지금 + N초"로 한꺼번에 맞춘다.
#
# 입찰을 먼저 넣어 둔 뒤에 돌린다. 낙찰 대상이 없으면 유찰이라
# 재려던 팬아웃(상위 입찰자 알림·거래 생성)이 아예 일어나지 않는다.
set -euo pipefail

AFTER_SECONDS="${1:-30}"
CONTAINER="${MYSQL_CONTAINER:-race-mysql}"

docker exec -i "$CONTAINER" mysql -urace -prace race <<SQL
update auction
set current_end_time = date_add(now(), interval ${AFTER_SECONDS} second)
where id between 9101 and 9199;

select id, status, current_end_time, top_bidder_id, current_price
from auction
where id between 9101 and 9199
order by id;
SQL

echo
echo "${AFTER_SECONDS}초 뒤 동시 마감으로 맞췄다."
echo "마감이 지난 뒤 ./load-test/measure-close-delay.sh 를 돌린다."

#!/usr/bin/env bash
# 시나리오 B의 결과 — 마감 지연을 잰다.
#
# 지연 = 상태가 확정된 시각 − 예정 마감 시각.
# 확정 시각을 따로 들고 있는 컬럼이 없어서 updated_at 을 쓴다. 마감 확정이
# 그 행의 마지막 쓰기라 값이 곧 확정 시각이다. 이후에 그 경매를 건드리면 값이 늘어난다.
#
# 가설 2의 판정: 지연이 경매 순번에 선형으로 증가하면, 팬아웃이 스케줄러 스레드
# 하나를 순서대로 점유하고 있다는 뜻이다. 순번과 무관하게 고르면 다른 원인이다.
set -euo pipefail

CONTAINER="${MYSQL_CONTAINER:-race-mysql}"

# 별칭은 영어로 둔다. 클라이언트 문자셋에 따라 한글 별칭이 깨져 구문 오류가 된다.
docker exec -i "$CONTAINER" mysql -urace -prace --default-character-set=utf8mb4 race <<'SQL'
select
    a.id,
    a.status,
    a.current_end_time                                        as scheduled_end,
    a.updated_at                                              as confirmed_at,
    timestampdiff(microsecond, a.current_end_time, a.updated_at) / 1000
                                                              as delay_ms,
    a.winner_id,
    (select count(*) from bid b where b.auction_id = a.id)     as bid_count
from auction a
where a.id between 9101 and 9199
order by a.id;
SQL

echo
echo "아직 SCHEDULED 나 IN_PROGRESS 인 행이 있으면 마감이 안 끝난 것이다. 잠시 뒤 다시 돌린다."
echo "앱 로그의 '경매 진행 틱 초과' 경고 빈도를 이 표와 함께 기록한다."

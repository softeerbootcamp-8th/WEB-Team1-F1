#!/usr/bin/env bash
# 측정용 계정을 회원가입 API로 만든다. 앱이 뜬 뒤 한 번만 돌리면 된다.
#
# SQL로 직접 넣지 않는 이유는 비밀번호다. 저장되는 값은 서버가 만든 해시라,
# 손으로 넣은 값으로는 로그인이 되지 않는다.
#
# 판매자는 입찰자 풀 밖의 별도 계정으로 만든다. 판매자 본인의 입찰은 거절되므로,
# 입찰에 쓰는 계정과 겹치면 그 VU 는 계속 400만 받는다.
#
# 이미 있는 계정은 409로 떨어지고 그건 정상이다. 다시 돌려도 안전하다.
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/guard.sh
source "$HERE/../lib/guard.sh"

BASE_URL="${BASE_URL:-http://localhost:8080}"
ACCOUNT_COUNT="${ACCOUNT_COUNT:-200}"
PREFIX="${ACCOUNT_PREFIX:-load_user_}"
SELLER_USERNAME="${SELLER_USERNAME:-load_seller}"
PASSWORD="${ACCOUNT_PASSWORD:-loadtest1234}"

assert_local_load_profile "$BASE_URL" || exit 1

created=0
exists=0
failed=0

signup() {
  local username="$1" phone="$2" real_name="$3" status

  status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "${BASE_URL}/api/users" \
    -H 'Content-Type: application/json' \
    -d "{
      \"username\": \"${username}\",
      \"email\": \"${username}@load.test\",
      \"password\": \"${PASSWORD}\",
      \"realName\": \"${real_name}\",
      \"phone\": \"${phone}\",
      \"role\": \"GENERAL\"
    }")

  case "$status" in
    20*) created=$((created + 1)) ;;
    409) exists=$((exists + 1)) ;;
    *)
      failed=$((failed + 1))
      [ "$failed" -le 3 ] && echo "  실패 ${username} — HTTP ${status}"
      ;;
  esac
}

echo "판매자 1명 + 입찰자 ${ACCOUNT_COUNT}명 준비 — ${BASE_URL}"

# 판매자. 번호 대역을 입찰자와 겹치지 않게 둔다 (휴대전화에 유일 제약이 있다).
signup "$SELLER_USERNAME" "01088880000" "부하판매"

for ((i = 0; i < ACCOUNT_COUNT; i++)); do
  # 010 + 숫자 8자리 형식
  signup "${PREFIX}${i}" "$(printf '0109%07d' "$i")" "부하${i}"
done

echo "생성 ${created} / 기존 ${exists} / 실패 ${failed}"

if [ "$failed" -gt 0 ]; then
  echo "실패가 있다. 스키마가 초기화되지 않았는지, 번호·아이디가 겹치지 않는지 확인한다."
  exit 1
fi

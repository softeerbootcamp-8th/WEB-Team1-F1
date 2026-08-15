#!/usr/bin/env bash
# 셸 스크립트용 안전장치. source 해서 쓴다.
#
# 이 스크립트들은 계정을 만들고 데이터를 지우고 넣는다. 주소만 바꿔 배포 환경을
# 향하면 운영 데이터를 지우게 되므로 로컬만 허용한다.

assert_local_url() {
  local url="$1"

  if [[ ! "$url" =~ ^https?://(localhost|127\.0\.0\.1)(:[0-9]+)?$ ]]; then
    echo "중단 — BASE_URL 이 로컬이 아니다: $url" >&2
    echo "이 스크립트는 데이터를 지우고 넣는다. 로컬에서만 돌린다." >&2
    return 1
  fi
}

# 지표 엔드포인트가 열려 있는지로 load 프로파일을 판정한다.
# 기본·운영 프로파일에서는 닫혀 있어 404 가 나온다.
assert_load_profile() {
  local url="$1"
  local status

  status=$(curl -s -o /dev/null -w '%{http_code}' "${url}/actuator/metrics" || echo 000)

  if [ "$status" != "200" ]; then
    echo "중단 — 지표 엔드포인트가 닫혀 있다 (HTTP ${status})." >&2
    echo "앱을 load 프로파일로 띄운다:" >&2
    echo "  cd backend && ./gradlew bootRun --args='--spring.profiles.active=load'" >&2
    return 1
  fi
}

# 앱 인스턴스가 둘 이상 같은 DB 를 보고 있으면 측정이 오염된다.
# 진행 스케줄러가 인스턴스마다 돌아 같은 경매를 두 번 마감하려 들기 때문이다.
warn_if_multiple_apps() {
  local count

  # 줄이 아니라 PID 로 센다. lsof 는 소켓마다 한 줄이라(IPv4·IPv6, 포트 여러 개)
  # 줄 수를 세면 인스턴스 하나가 여러 개로 보인다.
  #
  # 그리고 모든 인터페이스에 바인드한 것만 센다. 톰캣은 *:포트 로 열고
  # Gradle 데몬은 127.0.0.1:임의포트 로 열어서, 이 조건이 앱과 데몬을 가른다.
  count=$(lsof -nP -iTCP -sTCP:LISTEN 2>/dev/null \
    | awk '$1 == "java" && $9 ~ /^\*:/ { print $2 }' | sort -u | wc -l | tr -d ' ')

  if [ "${count:-0}" -gt 1 ]; then
    echo "경고 — 포트를 듣고 있는 java 프로세스가 ${count}개다." >&2
    echo "  앱이 둘 이상 같은 DB 를 보고 있으면 진행 스케줄러가 각각 돌아" >&2
    echo "  같은 경매를 두 번 마감하려 들고, 측정값이 오염된다." >&2
    echo "  측정용 인스턴스 하나만 남긴다." >&2
  fi
}

assert_local_load_profile() {
  local url="$1"

  assert_local_url "$url" || return 1
  assert_load_profile "$url" || return 1
  warn_if_multiple_apps
}

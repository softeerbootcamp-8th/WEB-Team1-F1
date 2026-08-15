import http from 'k6/http'
import { fail } from 'k6'
import { BASE_URL } from './config.js'

// 실행 전 안전장치.
//
// 하나 — 로컬만 허용한다. 이 스크립트는 계정을 만들고 데이터를 지우고 넣는다.
// 주소만 바꿔 배포 환경을 향하면 운영 데이터를 지우게 된다.
//
// 둘 — load 프로파일인지 확인한다. 지표 엔드포인트가 열려 있는지로 판정할 수 있다.
// 기본·운영 프로파일에서는 닫혀 있어서 404 가 나온다. 프로파일이 아니면
// ddl-auto 가 create 라 시드가 재기동마다 사라지고, 잴 지표 자체도 없다.
export function assertLocalLoadProfile() {
    if (!/^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(BASE_URL)) {
        fail(
            `BASE_URL 이 로컬이 아니다: ${BASE_URL}\n` +
            `이 스크립트는 데이터를 지우고 넣는다. 로컬에서만 돌린다.`,
        )
    }

    const res = http.get(`${BASE_URL}/actuator/metrics`)

    if (res.status !== 200) {
        fail(
            `지표 엔드포인트가 닫혀 있다 (HTTP ${res.status}).\n` +
            `앱을 load 프로파일로 띄운다:\n` +
            `  ./gradlew bootRun --args='--spring.profiles.active=load'`,
        )
    }
}

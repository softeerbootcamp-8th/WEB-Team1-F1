// 시나리오 D — 평상시 혼합 부하
//
// 재는 것: 조회 응답 분포, 그리고 인증이 붙은 요청과 안 붙은 요청의 차이
// 증명하려는 것: 세션을 별도 저장소로 옮긴 결정의 비용
//
// 로컬에서는 저장소 왕복이 0에 가까워 "얼마나 느린가"는 나오지 않는다. 대신 둘을 얻는다.
//   1. 왕복 횟수 — 코드의 성질이라 환경과 무관하다 (collect.sh 의 commandstats)
//   2. 공개 조회와 인증 조회의 응답 차이 — 같은 기계에서 인증 단계만 다른 두 요청의 비교
import http from 'k6/http'
import exec from 'k6/execution'
import { BASE_URL, SPIKE_AUCTION_ID, bidAmount } from '../lib/config.js'
import { loginAll, cookieFor, authHeaders } from '../lib/auth.js'
import { assertLocalLoadProfile } from '../lib/guard.js'
import { placeBid, treatRejectionAsExpected } from '../lib/bid.js'

treatRejectionAsExpected()

export const options = {
    scenarios: {
        warmup: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [{ duration: '30s', target: 30 }],
            gracefulRampDown: '0s',
            tags: { phase: 'warmup' },
        },
        measure: {
            executor: 'ramping-vus',
            startTime: '30s',
            startVUs: 30,
            stages: [
                { duration: '2m', target: 30 },
                { duration: '20s', target: 0 },
            ],
            tags: { phase: 'measure' },
        },
    },
    thresholds: {
        'http_req_failed{phase:measure}': ['rate<0.01'],
        bid_server_error: ['count==0'],
        bid_unexpected: ['count==0'],
        dropped_iterations: ['count==0'],

        // 이 둘의 차이가 이 시나리오의 산출물이다. 통과 여부가 아니라 값을 보려고 선언한다.
        'http_req_duration{phase:measure,name:GET /api/auctions (public)}': ['p(95)<60000'],
        'http_req_duration{phase:measure,name:GET /api/auth/me (authenticated)}': ['p(95)<60000'],
    },
}

export function setup() {
    assertLocalLoadProfile()

    return { cookies: loginAll() }
}

export default function (data) {
    const cookie = cookieFor(data.cookies, exec.vu.idInTest)
    const roll = exec.scenario.iterationInTest % 10

    if (roll < 7) {
        // 공개 목록 — 인증을 타지 않는다. 세션 저장소를 다녀오지 않는 쪽의 기준값이다.
        http.get(`${BASE_URL}/api/auctions`, { tags: { name: 'GET /api/auctions (public)' } })
        return
    }

    if (roll < 9) {
        // 인증만 하고 DB 작업은 거의 없다. 인증 단계 비용이 가장 크게 잡히는 자리다.
        http.get(`${BASE_URL}/api/auth/me`, {
            headers: authHeaders(cookie),
            tags: { name: 'GET /api/auth/me (authenticated)' },
        })
        return
    }

    placeBid(SPIKE_AUCTION_ID, bidAmount(exec.scenario.name, exec.scenario.iterationInTest), cookie)
}

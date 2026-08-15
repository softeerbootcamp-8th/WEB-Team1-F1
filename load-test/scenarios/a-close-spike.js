// 시나리오 A — 마감 직전 한 경매로 몰린다
//
// 재는 것: 입찰 응답 상위값, 거절(400)과 서버 오류(5xx)를 갈라서, 락 대기와 커넥션 대기
// 증명하려는 것: 입찰에 건 비관적 락이 이 규모에서 성립하는가
//
// 로컬 측정이라 응답 시간에 절대 합격선을 걸지 않는다. 기계가 달라서 나온 숫자다.
// 대신 동시 사용자를 늘렸을 때 상위값이 몇 배가 되는지를 본다.
//
// 워밍업을 별도 시나리오로 갈랐다. stages 로 붙여 두면 JVM 이 아직 기계어로
// 컴파일하기 전 값과 커넥션 풀 예열 전 값이 같은 지표에 섞이고, 사후에 걸러낼 수 없다.
// 집계는 phase:measure 태그가 붙은 것만 본다.
import exec from 'k6/execution'
import { SPIKE_AUCTION_ID, bidAmount } from '../lib/config.js'
import { loginAll, cookieFor } from '../lib/auth.js'
import { assertLocalLoadProfile } from '../lib/guard.js'
import { placeBid, treatRejectionAsExpected } from '../lib/bid.js'

treatRejectionAsExpected()

export const options = {
    scenarios: {
        warmup: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [{ duration: '30s', target: 50 }],
            gracefulRampDown: '0s',
            tags: { phase: 'warmup' },
        },
        measure: {
            executor: 'ramping-vus',
            startTime: '30s',
            startVUs: 50,
            stages: [
                { duration: '1m', target: 100 },
                { duration: '30s', target: 200 },
                { duration: '30s', target: 0 },
            ],
            gracefulRampDown: '10s',
            tags: { phase: 'measure' },
        },
    },
    thresholds: {
        // 환경과 무관하게 참이어야 하는 것만 건다. 응답 시간 기준은 걸지 않는다.
        'http_req_failed{phase:measure}': ['rate<0.01'],
        bid_server_error: ['count==0'],
        bid_unexpected: ['count==0'],

        // 부하 도구가 먼저 한계에 걸리면 서버 숫자가 아니다. 경고가 아니라 실패로 다룬다.
        dropped_iterations: ['count==0'],

        // 워밍업 구간을 뺀 응답 분포를 요약에 남기려고 선언한다.
        // 통과 여부가 아니라 값을 보기 위한 것이라 기준을 느슨하게 둔다.
        'http_req_duration{phase:measure}': ['p(95)<60000', 'p(99)<60000'],
    },
}

export function setup() {
    assertLocalLoadProfile()

    return { cookies: loginAll() }
}

export default function (data) {
    const cookie = cookieFor(data.cookies, exec.vu.idInTest)
    const amount = bidAmount(exec.scenario.name, exec.scenario.iterationInTest)

    placeBid(SPIKE_AUCTION_ID, amount, cookie)
}

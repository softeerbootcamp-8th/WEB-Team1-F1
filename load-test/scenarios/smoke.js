// 스모크 — 측정 파이프라인이 끝까지 도는지만 확인한다
//
// 성능을 재려는 것이 아니다. 확인하려는 것은 넷이다.
//   1. 안전장치가 동작하는가 (로컬인지, load 프로파일인지)
//   2. 계정으로 로그인해서 쿠키를 받는가
//   3. 입찰이 실제로 201 로 접수되는가 (시드와 금액 규칙이 맞는가)
//   4. 조회 두 종류가 200 인가
//
// 이게 통과한 뒤에 계정을 늘리고 본 측정으로 간다.
import http from 'k6/http'
import exec from 'k6/execution'
import { check } from 'k6'
import { BASE_URL, SPIKE_AUCTION_ID, START_PRICE, BID_STEP } from '../lib/config.js'
import { loginAll, cookieFor, authHeaders } from '../lib/auth.js'
import { assertLocalLoadProfile } from '../lib/guard.js'
import { placeBid, treatRejectionAsExpected, accepted } from '../lib/bid.js'

treatRejectionAsExpected()

const BIDDERS = Number(__ENV.ACCOUNT_COUNT || 3)

export const options = {
    scenarios: {
        smoke: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: BIDDERS,
            maxDuration: '2m',
        },
    },
    thresholds: {
        bid_server_error: ['count==0'],
        bid_unexpected: ['count==0'],
        // 순차 실행이라 거절이 나오면 안 된다. 나오면 금액 규칙이나 시드가 틀린 것이다.
        bid_rejected: ['count==0'],
        bid_accepted: [`count==${BIDDERS}`],
        checks: ['rate==1.0'],
    },
}

export function setup() {
    assertLocalLoadProfile()
    console.log(`안전장치 통과 — ${BASE_URL} / load 프로파일 확인`)

    const cookies = loginAll(BIDDERS)
    console.log(`로그인 ${cookies.length}건 성공`)

    return { cookies }
}

export default function (data) {
    const i = exec.vu.iterationInInstance
    const cookie = cookieFor(data.cookies, i + 1)

    // 공개 조회
    const list = http.get(`${BASE_URL}/api/auctions`, {
        tags: { name: 'GET /api/auctions (public)' },
    })
    check(list, { '공개 목록 200': (r) => r.status === 200 })

    // 인증 조회
    const me = http.get(`${BASE_URL}/api/auth/me`, {
        headers: authHeaders(cookie),
        tags: { name: 'GET /api/auth/me (authenticated)' },
    })
    check(me, { '인증 조회 200': (r) => r.status === 200 })

    // 입찰 — 순차로 한 칸씩 올린다
    const res = placeBid(SPIKE_AUCTION_ID, START_PRICE + (i + 1) * BID_STEP, cookie)
    check(res, { '입찰 201': (r) => r.status === 201 })
}

export function teardown() {
    console.log(`입찰 접수 ${accepted.name} — 요약의 bid_accepted count 가 ${BIDDERS} 인지 확인한다`)
}

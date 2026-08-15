// 시나리오 B — 여러 경매가 같은 초에 마감된다
//
// 재는 것: 마감 지연(확정 시각 − 예정 마감), 진행 틱 초과 로그 빈도
// 증명하려는 것: 커밋 후 알림 전달을 같은 스레드에서 동기로 하는 구조가 성립하는가
//
// 이 스크립트는 부하가 아니라 준비다. 낙찰 대상이 생기도록 경매마다 입찰자를
// 정해진 수만큼 확실히 넣는 것이 목적이고, 정작 재려는 것은 그 뒤에 스케줄러가 하는 일이다.
//
// 그래서 일부러 VU 하나로 순차 실행한다. 동시에 넣으면 같은 경매에서 뒤늦게 도착한
// 낮은 금액이 400으로 거절되어 경매당 입찰자 수가 목표보다 적게 남는다.
// 팬아웃 비용은 입찰자 수에 비례하므로, 그 수가 흔들리면 측정 자체가 무의미해진다.
import exec from 'k6/execution'
import { fail } from 'k6'
import { FANOUT_AUCTION_IDS, START_PRICE, BID_STEP, ACCOUNT_COUNT } from '../lib/config.js'
import { loginAll, cookieFor } from '../lib/auth.js'
import { assertLocalLoadProfile } from '../lib/guard.js'
import { placeBid, treatRejectionAsExpected, accepted, rejected } from '../lib/bid.js'

treatRejectionAsExpected()

// 경매당 입찰자 수. 상위 입찰 알림이 그 경매에 입찰했던 사람 전체로 나가므로
// 팬아웃 비용이 이 수에 비례한다.
const BIDDERS_PER_AUCTION = Number(__ENV.BIDDERS_PER_AUCTION || 20)

const TOTAL = FANOUT_AUCTION_IDS.length * BIDDERS_PER_AUCTION

export const options = {
    scenarios: {
        seedBids: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: TOTAL,
            maxDuration: '10m',
        },
    },
    thresholds: {
        bid_server_error: ['count==0'],
        bid_unexpected: ['count==0'],
        // 순차로 넣으므로 거절이 나오면 안 된다. 나오면 설계 전제가 깨진 것이다.
        bid_rejected: ['count==0'],
    },
}

export function setup() {
    assertLocalLoadProfile()

    if (BIDDERS_PER_AUCTION > ACCOUNT_COUNT) {
        fail(
            `경매당 입찰자 ${BIDDERS_PER_AUCTION}명이 계정 수 ${ACCOUNT_COUNT}개보다 많다. ` +
            `같은 계정이 연속으로 최고가가 되어 거절된다.`,
        )
    }

    return { cookies: loginAll() }
}

export default function (data) {
    const i = exec.vu.iterationInInstance

    // 경매 하나를 끝까지 채운 뒤 다음 경매로 넘어간다.
    // 경매를 번갈아 돌면 어느 경매가 몇 명까지 찼는지 중간 상태가 불분명해진다.
    const auctionIndex = Math.floor(i / BIDDERS_PER_AUCTION)
    const bidderIndex = i % BIDDERS_PER_AUCTION

    const auctionId = FANOUT_AUCTION_IDS[auctionIndex]

    // 경매마다 다시 0부터 올린다. 경매별로 현재가가 따로 관리되기 때문이다.
    const amount = START_PRICE + (bidderIndex + 1) * BID_STEP

    // 입찰자마다 다른 계정이라 현재 최고가인 사람의 재입찰에 걸리지 않는다.
    const cookie = cookieFor(data.cookies, bidderIndex + 1)

    placeBid(auctionId, amount, cookie)
}

export function teardown() {
    console.log(
        `입찰 적재 — 성공 ${accepted.name} / 거절 ${rejected.name} (요약의 count 를 본다)\n` +
        `경매 ${FANOUT_AUCTION_IDS.length}개 × ${BIDDERS_PER_AUCTION}명 = ${TOTAL}건이 목표다.\n` +
        `\n다음 순서:\n` +
        `  ./load-test/seed/arm-fanout.sh 30    30초 뒤 동시 마감으로 맞춘다\n` +
        `  ./load-test/measure-close-delay.sh   마감이 지난 뒤 실행`,
    )
}

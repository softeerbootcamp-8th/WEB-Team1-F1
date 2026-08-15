import http from 'k6/http'
import { Counter } from 'k6/metrics'
import { BASE_URL } from './config.js'
import { authHeaders } from './auth.js'

export const accepted = new Counter('bid_accepted')
export const rejected = new Counter('bid_rejected')
export const serverError = new Counter('bid_server_error')
export const unexpected = new Counter('bid_unexpected')

// 입찰 응답의 급을 코드에서 확인하고 맞춘 것(BidErrorCode):
//
//   201  접수
//   409  정상 경합 거절 — SELF_OUTBID(이미 최고가) · BID_AMOUNT_TOO_LOW ·
//        BID_AMOUNT_NOT_ALIGNED · AUCTION_NOT_LIVE
//   403  SELLER_CANNOT_BID · EVALUATOR_CANNOT_BID
//   400  BID_AMOUNT_TOO_HIGH (형식)
//   404  경매·사용자 없음
//
// 동시 입찰에서 남이 먼저 올려 밀리는 것은 409 다. 이것이 이 시나리오의 정상 동작이고,
// 실패로 세면 오류율이 실제와 무관하게 치솟는다.
//
// 반대로 403·400·404 는 정상 동작이 아니다. 판매자 계정으로 입찰했거나, 금액 계산이
// 틀렸거나, 시드가 안 들어간 것이다. 거절과 같이 묶으면 그 결함이 조용히 통과한다.
const BUSINESS_REJECTION = 409

export function treatRejectionAsExpected() {
    // options 에 responseCallback 필드를 적는 방식은 k6 에 없다.
    // 그렇게 쓰면 조용히 무시되고 409 가 그대로 실패로 집계된다.
    http.setResponseCallback(http.expectedStatuses(200, 201, BUSINESS_REJECTION))
}

export function placeBid(auctionId, amount, cookie) {
    const res = http.post(
        `${BASE_URL}/api/auctions/${auctionId}/bids`,
        JSON.stringify({ amount }),
        { headers: authHeaders(cookie), tags: { name: 'POST /api/auctions/{id}/bids' } },
    )

    if (res.status === 201) {
        accepted.add(1)
    } else if (res.status === BUSINESS_REJECTION) {
        rejected.add(1)
    } else if (res.status >= 500) {
        serverError.add(1)
        logSample('서버 오류', res)
    } else {
        unexpected.add(1)
        logSample('예상 밖 응답', res)
    }

    return res
}

// 200 VU 가 같은 오류를 내면 로그가 그것만으로 차고, 로그 기록 자체가 측정을 오염시킨다.
let logged = 0

function logSample(prefix, res) {
    if (logged < 5) {
        logged++
        console.error(`${prefix} ${res.status}: ${res.body}`)
    }
}

export function authorizedHeaders(cookie) {
    return authHeaders(cookie)
}

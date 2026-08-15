// 시드(fixture.sql)와 반드시 같은 값을 써야 한다. 한쪽만 바꾸면 입찰이 전부 400이 된다.

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'

// 입찰자 계정 수. accounts.sh 가 만든 개수와 같아야 한다.
// VU 가 이보다 많으면 계정이 돌려 쓰이고, 같은 사람이 연속으로 최고가가 되어 400이 는다.
export const ACCOUNT_COUNT = Number(__ENV.ACCOUNT_COUNT || 200)

export const ACCOUNT_PREFIX = 'load_user_'
export const ACCOUNT_PASSWORD = 'loadtest1234'

// 판매자는 입찰자 풀 밖의 별도 계정이다. 판매자 본인의 입찰은 거절되므로
// 입찰 계정과 겹치면 그 VU 는 계속 400만 받는다.
// 이 이름을 fixture.sql 도 참조한다. 바꾸면 양쪽을 같이 고친다.
export const SELLER_USERNAME = 'load_seller'

// 시나리오 A가 몰릴 경매. fixture.sql 의 9001 과 같아야 한다.
export const SPIKE_AUCTION_ID = Number(__ENV.SPIKE_AUCTION_ID || 9001)

// 시나리오 B가 같은 초에 마감시킬 경매들.
export const FANOUT_AUCTION_IDS = (__ENV.FANOUT_AUCTION_IDS || '9101,9102,9103,9104,9105')
    .split(',')
    .map(Number)

// 시작가 1억. 구간표의 마지막 구간(1억 이상)에 들어가 상승가가 50만원으로 고정된다.
// 가격이 올라도 구간이 바뀌지 않아, k6 가 구간을 다시 판정하지 않고 금액을 만들 수 있다.
export const START_PRICE = 100_000_000
export const BID_STEP = 500_000

// 시나리오별 금액 오프셋.
//
// iterationInTest 는 시나리오마다 0부터 다시 센다. 워밍업과 본 측정을 시나리오로 갈라 놓으면
// 본 측정의 첫 금액이 워밍업이 이미 올려놓은 현재가보다 낮아져 전량 400이 된다.
// 그래서 뒤에 도는 시나리오에 앞 시나리오가 쓰지 않을 만큼의 번호 블록을 미리 비워 준다.
export const AMOUNT_OFFSET = {
    warmup: 0,
    measure: 100_000,
}

export function bidAmount(scenarioName, iterationInTest) {
    const offset = AMOUNT_OFFSET[scenarioName] ?? 0

    return START_PRICE + (offset + iterationInTest + 1) * BID_STEP
}

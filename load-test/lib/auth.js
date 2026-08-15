import http from 'k6/http'
import { fail } from 'k6'
import { BASE_URL, ACCOUNT_COUNT, ACCOUNT_PREFIX, ACCOUNT_PASSWORD } from './config.js'

// 로그인을 setup 에서만 한다. 비밀번호 해시는 의도적으로 느린 연산이라
// 부하 반복 안에 넣으면 재려던 것 대신 그 연산을 재게 된다.
export function loginAll(count = ACCOUNT_COUNT) {
    const cookies = []

    for (let i = 0; i < count; i++) {
        const username = `${ACCOUNT_PREFIX}${i}`
        const res = http.post(
            `${BASE_URL}/api/auth/login`,
            JSON.stringify({ username, password: ACCOUNT_PASSWORD }),
            { headers: { 'Content-Type': 'application/json' } },
        )

        if (res.status !== 200) {
            fail(`로그인 실패 ${username}: ${res.status} ${res.body}`)
        }

        // 세션 토큰은 Set-Cookie 로만 나온다. 응답 본문에는 없다.
        const setCookie = res.headers['Set-Cookie']
        if (!setCookie) {
            fail(`Set-Cookie 없음 ${username}`)
        }

        cookies.push(setCookie.split(';')[0])
    }

    return cookies
}

// VU 번호로 계정을 고른다. 계정 수보다 VU 가 많으면 돌려 쓴다.
export function cookieFor(cookies, vu) {
    return cookies[(vu - 1) % cookies.length]
}

export function authHeaders(cookie) {
    return {
        'Content-Type': 'application/json',
        Cookie: cookie,
    }
}

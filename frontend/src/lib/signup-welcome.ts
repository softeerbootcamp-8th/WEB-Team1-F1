/**
 * 가입 직후인지 알리는 일회용 표시.
 *
 * 환영 알림은 실시간으로 도착할 수 없다. 가입이 세션을 발급하지 않아 발행 시점에 그 회원의 구독이
 * 없고, 화면은 그 뒤에 로그인해서 열린다. 그래서 알림함에는 남지만 안내는 뜨지 않는다.
 * 그 한 번만 화면이 대신 안내를 띄우도록, 가입 화면이 표시를 남기고 알림 쪽이 읽고 지운다.
 *
 * **이 안내는 실시간 도착이 아니라 화면이 만든 것이다.** 실시간 경로를 증명하지 않는다.
 *
 * localStorage 가 아니라 sessionStorage 를 쓴다. 탭을 닫으면 같이 사라져야 하고, 다른 탭의 가입이
 * 이 탭의 안내를 만들어서도 안 된다.
 *
 * 가입 화면과 알림이 같은 문자열을 각자 적으면 한쪽만 고쳐도 조용히 어긋나므로 여기로 모은다.
 */
const KEY = 'race.justSignedUp'

export function markJustSignedUp(userId: number): void {
  try {
    sessionStorage.setItem(KEY, String(userId))
  } catch {
    // 환영 안내용 표시는 부가 기능이므로 저장소 오류가 회원가입 성공을 뒤집지 않는다
  }
}

/** 현재 회원의 표시였으면 true다. 누구의 표시든 한 번 확인하면 지워 다른 계정으로 넘어가지 않는다. */
export function consumeJustSignedUp(userId: number): boolean {
  try {
    const markedUserId = sessionStorage.getItem(KEY)
    sessionStorage.removeItem(KEY)

    return markedUserId === String(userId)
  } catch {
    return false
  }
}

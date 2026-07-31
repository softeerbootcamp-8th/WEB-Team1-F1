/**
 * 표시용 포매팅 헬퍼 모음.
 * 금액/시간/닉네임 마스킹 등 화면 전반에서 재사용한다.
 */

/** 원화 표기. ex) 12_500_000 → "1,250만원" 스타일이 아닌 정수 "12,500,000원" */
export function formatKRW(value: number): string {
  return `${value.toLocaleString('ko-KR')}원`
}

/** 만원 단위 축약. ex) 12_500_000 → "1,250만" */
export function formatManwon(value: number): string {
  const man = Math.round(value / 10000)
  return `${man.toLocaleString('ko-KR')}만`
}

/** 주행거리. ex) 45123 → "45,123km" */
export function formatMileage(km: number): string {
  return `${km.toLocaleString('ko-KR')}km`
}

/**
 * 닉네임 마스킹. 가운데 글자를 * 로 가린다.
 * "김민진" → "김*진", "이수" → "이*", "박" → "박"
 */
export function maskNickname(nickname: string): string {
  if (nickname.length <= 1) return nickname
  if (nickname.length === 2) return `${nickname[0]}*`
  const first = nickname[0]
  const last = nickname[nickname.length - 1]
  return `${first}${'*'.repeat(nickname.length - 2)}${last}`
}

/** 남은 시간(ms)을 mm:ss 또는 hh:mm:ss 로. 음수는 00:00 */
export function formatDuration(ms: number): string {
  if (ms <= 0) return '00:00'
  const totalSec = Math.floor(ms / 1000)
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const s = totalSec % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `${pad(h)}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`
}

/** 상대 시간. ex) "3분 전", "방금" */
export function formatRelativeTime(iso: string, now: number = Date.now()): string {
  const diff = now - new Date(iso).getTime()
  const sec = Math.floor(diff / 1000)
  if (sec < 10) return '방금'
  if (sec < 60) return `${sec}초 전`
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min}분 전`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr}시간 전`
  const day = Math.floor(hr / 24)
  return `${day}일 전`
}

/** 시각 표기. ex) "오후 3:04" */
export function formatClock(iso: string): string {
  return new Date(iso).toLocaleTimeString('ko-KR', {
    hour: 'numeric',
    minute: '2-digit',
  })
}

/** 날짜+시각. ex) "7월 22일 오후 3:04" */
export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    month: 'long',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

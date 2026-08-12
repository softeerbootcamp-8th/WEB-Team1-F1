/**
 * 입찰을 받는 시각인지. 마감 정각은 받지 않는다, 서버 판정과 경계를 같게 둔다
 */
export function acceptsBidAt(endAtIso: string, nowMs: number): boolean {
  return nowMs < new Date(endAtIso).getTime()
}

/**
 * 마감까지 남은 밀리초, 이미 지났으면 0이다
 */
export function millisUntil(endAtIso: string, nowMs: number): number {
  return Math.max(0, new Date(endAtIso).getTime() - nowMs)
}

/** 금액 상한 1조원, 서버가 같은 값으로 막는다 */
export const MAX_AMOUNT = 1_000_000_000_000

/** 만원 단위로 받는 입력이 쓰는 같은 상한 */
export const MAX_AMOUNT_MANWON = MAX_AMOUNT / 10_000

/** 만원 단위 입력값이 상한 안인지, 쉼표가 붙은 표시 문자열을 그대로 받는다 */
export function withinManwonCap(formatted: string): boolean {
  return Number(formatted.replaceAll(',', '') || '0') <= MAX_AMOUNT_MANWON
}

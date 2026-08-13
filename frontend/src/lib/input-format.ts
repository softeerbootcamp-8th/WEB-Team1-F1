/** 입력값에서 숫자 이외의 문자를 제거한다. */
export function onlyDigits(value: string): string {
  return value.replace(/\D/g, '')
}

/**
 * 휴대전화 번호 입력값에 하이픈을 붙인다.
 * 이미 하이픈이 있거나 다른 문자가 섞인 붙여넣기도 숫자만 추려 다시 포맷한다.
 */
export function formatPhoneInput(value: string): string {
  const digits = onlyDigits(value).slice(0, 11)

  if (digits.length <= 3) return digits
  if (digits.length <= 7) return `${digits.slice(0, 3)}-${digits.slice(3)}`
  if (digits.length <= 10) {
    return `${digits.slice(0, 3)}-${digits.slice(3, -4)}-${digits.slice(-4)}`
  }
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`
}

/** 서버 요청용 숫자 형태의 휴대전화 번호로 되돌린다. */
export function parsePhoneInput(value: string): string {
  return onlyDigits(value).slice(0, 11)
}

/**
 * 숫자 입력값에 천 단위 쉼표를 붙인다.
 * 쉼표나 단위가 포함된 붙여넣기도 숫자만 추려 다시 포맷한다.
 */
export function formatNumericInput(value: string | number, maxDigits?: number): string {
  const digits = onlyDigits(String(value)).slice(0, maxDigits)
  if (!digits) return ''

  const normalized = digits.replace(/^0+(?=\d)/, '')
  return normalized.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** 쉼표가 표시된 숫자 입력값을 API 요청에 사용할 수 있는 숫자로 바꾼다. */
export function parseNumericInput(value: string): number {
  return Number(onlyDigits(value))
}

/** 자동 포맷 뒤에도 커서 앞에 있던 숫자 개수를 기준으로 위치를 복원한다. */
export function getCaretPosition(value: string, digitCount: number): number {
  if (digitCount === 0) return 0

  let seenDigits = 0
  let position = value.length

  for (let index = 0; index < value.length; index += 1) {
    if (/\d/.test(value[index])) seenDigits += 1
    if (seenDigits === digitCount) {
      position = index + 1
      break
    }
  }

  while (position < value.length && /\D/.test(value[position])) position += 1
  return position
}

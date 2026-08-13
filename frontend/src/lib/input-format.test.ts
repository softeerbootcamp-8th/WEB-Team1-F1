import { describe, expect, it } from 'vitest'

import {
  formatNumericInput,
  formatPhoneInput,
  getCaretPosition,
  parseNumericInput,
  parsePhoneInput,
} from './input-format'

describe('phone input format', () => {
  it('입력 중인 휴대전화 번호에 하이픈을 붙인다', () => {
    expect(formatPhoneInput('010')).toBe('010')
    expect(formatPhoneInput('0101234')).toBe('010-1234')
    expect(formatPhoneInput('01012345678')).toBe('010-1234-5678')
    expect(formatPhoneInput('0111234567')).toBe('011-123-4567')
  })

  it('형식 문자나 문자가 섞여도 숫자를 기준으로 다시 포맷한다', () => {
    expect(formatPhoneInput('010-1234-5678')).toBe('010-1234-5678')
    expect(formatPhoneInput('010 1234 ab5678')).toBe('010-1234-5678')
  })

  it('서버에는 숫자만 전달할 수 있도록 파싱한다', () => {
    expect(parsePhoneInput('010-1234-5678')).toBe('01012345678')
  })
})

describe('numeric input format', () => {
  it('천 단위 쉼표를 붙이고 앞자리 0을 정리한다', () => {
    expect(formatNumericInput('45000')).toBe('45,000')
    expect(formatNumericInput('00045000')).toBe('45,000')
  })

  it('쉼표나 단위가 섞인 붙여넣기를 다시 포맷한다', () => {
    expect(formatNumericInput('45,000km')).toBe('45,000')
  })

  it('최대 숫자 자릿수를 제한할 수 있다', () => {
    expect(formatNumericInput('1234567', 6)).toBe('123,456')
  })

  it('API 요청에 사용할 숫자로 파싱한다', () => {
    expect(parseNumericInput('45,000')).toBe(45_000)
  })
})

describe('getCaretPosition', () => {
  it('자동으로 추가된 형식 문자 다음으로 커서를 옮긴다', () => {
    expect(getCaretPosition('010-1', 3)).toBe(4)
    expect(getCaretPosition('45,000', 2)).toBe(3)
  })
})

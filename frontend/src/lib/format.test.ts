import { describe, expect, it } from 'vitest'

import {
  formatDuration,
  formatManwon,
  formatRelativeTime,
  maskNickname,
} from './format'

// 시각 표기(formatClock, formatDateTime)는 실행 환경의 시간대에 따라 값이 달라져
// 로컬과 CI 에서 결과가 갈린다. 병합 조건으로 걸 검사에 넣지 않는다.

describe('maskNickname', () => {
  it('가운데를 가리고 처음과 끝만 남긴다', () => {
    expect(maskNickname('김민진')).toBe('김*진')
    expect(maskNickname('정동현입니다')).toBe('정****다')
  })

  it('두 글자는 뒤를 가린다', () => {
    expect(maskNickname('이수')).toBe('이*')
  })

  // 서버가 가입 단계에서 한 글자 닉네임을 막으므로 실제로는 도달하지 않는다.
  // 가릴 자리가 없어 그대로 두는 것이 지금 동작이고, 방어적으로 남겨둔다.
  it('한 글자는 가리지 않는다', () => {
    expect(maskNickname('박')).toBe('박')
    expect(maskNickname('')).toBe('')
  })
})

describe('formatDuration', () => {
  it('한 시간 미만은 분과 초로 적는다', () => {
    expect(formatDuration(59_000)).toBe('00:59')
    expect(formatDuration(60_000)).toBe('01:00')
    expect(formatDuration(3_599_000)).toBe('59:59')
  })

  it('한 시간부터 시간 자리가 붙는다', () => {
    expect(formatDuration(3_600_000)).toBe('01:00:00')
    expect(formatDuration(3_661_000)).toBe('01:01:01')
  })

  it('이미 지난 시간은 00:00 이다', () => {
    expect(formatDuration(0)).toBe('00:00')
    expect(formatDuration(-5_000)).toBe('00:00')
  })
})

describe('formatManwon', () => {
  it('만 단위로 끊는다', () => {
    expect(formatManwon(12_500_000)).toBe('1,250만')
  })

  // 억을 "10,000만"으로 쓰면 자릿수를 세어야 금액을 알 수 있다
  it('억을 넘으면 억으로 끊는다', () => {
    expect(formatManwon(100_000_000)).toBe('1억')
    expect(formatManwon(134_000_000)).toBe('1억 3,400만')
  })

  it('억 단위가 딱 떨어지면 만을 붙이지 않는다', () => {
    expect(formatManwon(300_000_000)).toBe('3억')
  })
})

describe('formatRelativeTime', () => {
  // 기준 시각을 인자로 받으므로 시계를 고정하지 않아도 결과가 흔들리지 않는다
  const now = Date.parse('2026-08-10T12:00:00Z')
  const ago = (ms: number) => new Date(now - ms).toISOString()

  it('십 초 안쪽은 방금이다', () => {
    expect(formatRelativeTime(ago(0), now)).toBe('방금')
    expect(formatRelativeTime(ago(9_000), now)).toBe('방금')
  })

  it('분 단위로 넘어가기 전에는 초로 적는다', () => {
    expect(formatRelativeTime(ago(10_000), now)).toBe('10초 전')
    expect(formatRelativeTime(ago(59_000), now)).toBe('59초 전')
  })

  it('시간이 커질수록 단위를 올린다', () => {
    expect(formatRelativeTime(ago(60_000), now)).toBe('1분 전')
    expect(formatRelativeTime(ago(3_600_000), now)).toBe('1시간 전')
    expect(formatRelativeTime(ago(86_400_000), now)).toBe('1일 전')
    expect(formatRelativeTime(ago(3 * 86_400_000), now)).toBe('3일 전')
  })
})

import { describe, expect, it } from 'vitest'

import { acceptsBidAt, millisUntil } from './deadline'

const END_AT = '2026-08-03T18:50:00.000Z'
const endMs = new Date(END_AT).getTime()

describe('acceptsBidAt', () => {
  it('마감 전에는 받는다', () => {
    expect(acceptsBidAt(END_AT, endMs - 1)).toBe(true)
  })

  // 서버는 마감 정각에 입찰을 거절하고 종료는 허용한다, 화면이 반대로 보면 눌릴 버튼이 거절당한다
  it('마감 정각에는 받지 않는다', () => {
    expect(acceptsBidAt(END_AT, endMs)).toBe(false)
  })

  it('마감 뒤에는 받지 않는다', () => {
    expect(acceptsBidAt(END_AT, endMs + 1000)).toBe(false)
  })

  it('마감이 밀리면 원래 마감을 넘겨도 받는다', () => {
    const extended = new Date(endMs + 180_000).toISOString()

    expect(acceptsBidAt(extended, endMs + 1000)).toBe(true)
  })
})

describe('millisUntil', () => {
  it('남은 시간을 밀리초로 준다', () => {
    expect(millisUntil(END_AT, endMs - 5_000)).toBe(5_000)
  })

  it('이미 지났으면 0이다', () => {
    expect(millisUntil(END_AT, endMs + 5_000)).toBe(0)
  })
})

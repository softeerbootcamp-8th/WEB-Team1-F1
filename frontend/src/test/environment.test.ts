import { renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { useCountdown } from '@/hooks/use-countdown'

// 이 파일은 기능이 아니라 테스트 환경을 지킨다. jsdom 과 렌더가 빠지면 여기서 먼저 깨진다
describe('테스트 환경', () => {
  it('훅을 렌더해 상태를 읽을 수 있다', () => {
    const target = new Date(Date.now() + 60_000).toISOString()

    const { result } = renderHook(() => useCountdown(target))

    expect(result.current.remaining).toBeGreaterThan(0)
    expect(result.current.isElapsed).toBe(false)
  })
})

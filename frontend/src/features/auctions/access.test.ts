import { describe, expect, it } from 'vitest'

import { auctionScopeForRole, canViewMyAuctions } from './access'

describe('역할별 경매 목록 범위', () => {
  it('평가사가 나의 경매 주소를 입력해도 모든 경매로 고정한다', () => {
    expect(auctionScopeForRole('MINE', 'EVALUATOR')).toBe('ALL')
    expect(canViewMyAuctions('EVALUATOR')).toBe(false)
  })

  it.each(['GENERAL', 'DEALER'] as const)(
    '%s 회원은 나의 경매를 볼 수 있다',
    (role) => {
      expect(auctionScopeForRole('MINE', role)).toBe('MINE')
      expect(canViewMyAuctions(role)).toBe(true)
    },
  )
})

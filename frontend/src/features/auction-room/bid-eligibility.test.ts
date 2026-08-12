import { describe, expect, it } from 'vitest'

import { bidBlockOf } from './bid-eligibility'

describe('bidBlockOf', () => {
  it('평가사는 남의 경매에서도 막힌다', () => {
    expect(bidBlockOf('EVALUATOR', false)).toBe('EVALUATOR')
  })

  it('차를 내놓은 사람은 자기 경매에서 막힌다', () => {
    expect(bidBlockOf('GENERAL', true)).toBe('SELLER')
  })

  // 서버는 평가사를 먼저 본다. 화면이 반대로 보면 같은 사람이 두 화면에서 다른 사유를 듣는다
  it('자기 차를 내놓은 평가사에게는 평가사 사유가 먼저다', () => {
    expect(bidBlockOf('EVALUATOR', true)).toBe('EVALUATOR')
  })

  it('그 밖에는 막지 않는다', () => {
    expect(bidBlockOf('GENERAL', false)).toBeNull()
    expect(bidBlockOf('DEALER', false)).toBeNull()
  })
})

import { describe, expect, it } from 'vitest'

import { getAuctionStatusMeta } from './utils'

describe('경매 상태 표시', () => {
  it.each([
    ['SCHEDULED', '경매 예정'],
    ['IN_PROGRESS', '경매 진행 중'],
    ['ENDED', '낙찰 완료'],
    ['FAILED', '유찰'],
  ] as const)('%s 상태를 %s로 표시한다', (status, label) => {
    expect(getAuctionStatusMeta(status).label).toBe(label)
  })
})

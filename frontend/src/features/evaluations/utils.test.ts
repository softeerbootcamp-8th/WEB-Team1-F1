import { describe, expect, it } from 'vitest'

import { getAuctionStatusMeta, getEvaluationStatusMeta } from './utils'

describe('평가 상태 표시', () => {
  it('평가사에게 배정 상태 대신 진단 전으로 표시한다', () => {
    expect(getEvaluationStatusMeta('REQUESTED', true, 'evaluator').label).toBe('진단 전')
  })

  it('판매자에게는 평가사 배정 상태를 표시한다', () => {
    expect(getEvaluationStatusMeta('REQUESTED', true, 'seller').label).toBe('평가사 배정됨')
  })
})

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

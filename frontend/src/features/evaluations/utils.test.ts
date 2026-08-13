import { describe, expect, it } from 'vitest'

import {
  canRegisterAuction,
  getAuctionBlockReason,
  getAuctionStatusMeta,
  getEvaluationStatusMeta,
} from './utils'

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

describe('경매 등록 가능 여부', () => {
  it.each(['SCHEDULED', 'IN_PROGRESS', 'ENDED'] as const)(
    '%s 상태면 재출품을 막는다',
    (status) => {
      expect(canRegisterAuction(status)).toBe(false)
    },
  )

  it('유찰된 차량은 다시 출품할 수 있다', () => {
    expect(canRegisterAuction('FAILED')).toBe(true)
  })

  it('경매 이력이 없으면 출품할 수 있다', () => {
    expect(canRegisterAuction(null)).toBe(true)
  })

  // 목록을 못 읽었다고 출품을 막지는 않는다. 실제 중복은 서버가 409로 돌려보낸다
  it('상태를 모르면 막지 않는다', () => {
    expect(canRegisterAuction(undefined)).toBe(true)
  })

  it.each([
    ['SCHEDULED', '경매 시작을 기다리는 중입니다'],
    ['IN_PROGRESS', '경매가 진행 중입니다'],
    ['ENDED', '낙찰이 끝난 차량입니다'],
  ] as const)('%s 상태의 이유를 설명한다', (status, prefix) => {
    expect(getAuctionBlockReason(status)).toContain(prefix)
  })
})

import { describe, expect, it } from 'vitest'

import {
  canRegisterAuction,
  getAuctionBlockReason,
  getAuctionStatusMeta,
  getEvaluationStatusMeta,
} from './utils'

describe('평가 상태 표시', () => {
  it('배정 전 신청을 배정 대기로 표시한다', () => {
    expect(getEvaluationStatusMeta('REQUESTED', false).label).toBe('배정 대기')
  })

  it('배정된 신청을 평가 진행 중으로 표시한다', () => {
    expect(getEvaluationStatusMeta('REQUESTED', true).label).toBe('평가 진행 중')
  })

  it('진단이 끝나면 차량 진단 완료로 표시한다', () => {
    expect(getEvaluationStatusMeta('APPROVED', true).label).toBe('차량 진단 완료')
  })

  it('반려된 신청은 반려로 표시한다', () => {
    // 목록의 종료 탭 칩도 같은 말을 쓴다. 한쪽만 길면 같은 상태가 화면마다 다른 이름이 된다
    expect(getEvaluationStatusMeta('REJECTED', true).label).toBe('반려')
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

import { describe, expect, it } from 'vitest'

import { countRequests, needsListing, requestScopeOf, selectRequests } from './request-scope'
import type { EvaluationSummary } from './types'

function request(overrides: Partial<EvaluationSummary> = {}): EvaluationSummary {
  return {
    evaluationId: 1,
    status: 'REQUESTED',
    assigned: false,
    plateNumber: '12가3456',
    manufacturer: 'HYUNDAI',
    model: '그랜저 IG',
    modelYear: 2021,
    visitDate: '2026-08-20',
    visitAddress: '서울 성동구 왕십리로 83',
    requestedAt: '2026-08-05T15:30:00',
    ...overrides,
  }
}

describe('판매자 신청 내역의 진행 중 · 종료 구분', () => {
  it('진단이 끝나도 출품이 남아 있으면 진행 중이다', () => {
    // 판매자에게 진단 완료는 끝이 아니라 할 일이 생기는 지점이다.
    // 평가사 기준을 그대로 쓰면 가장 먼저 봐야 할 건이 종료 탭으로 숨는다
    expect(requestScopeOf(request({ status: 'APPROVED' }))).toBe('ACTIVE')
    expect(requestScopeOf(request({ status: 'APPROVED', auctionStatus: 'IN_PROGRESS' })))
      .toBe('ACTIVE')
  })

  it('유찰은 끝이 아니다', () => {
    // 같은 진단 차량으로 다시 출품할 수 있어 오히려 손을 대야 하는 쪽이다
    const failed = request({ status: 'APPROVED', auctionStatus: 'FAILED' })

    expect(requestScopeOf(failed)).toBe('ACTIVE')
    expect(needsListing(failed)).toBe(true)
  })

  it('반려와 낙찰 완료만 종료다', () => {
    // 낙찰 뒤의 일은 마이페이지의 판매 내역이 거래로 이어받는다
    expect(requestScopeOf(request({ status: 'REJECTED' }))).toBe('CLOSED')
    expect(requestScopeOf(request({ status: 'APPROVED', auctionStatus: 'ENDED' }))).toBe('CLOSED')
  })

  it('진행 중에서는 출품할 수 있는 건이 맨 위로 온다', () => {
    // given : 서버는 접수 최신순으로 준다
    const evaluations = [
      request({ evaluationId: 3, status: 'REQUESTED' }),
      request({ evaluationId: 2, status: 'APPROVED', auctionStatus: 'SCHEDULED' }),
      request({ evaluationId: 1, status: 'APPROVED' }),
    ]

    // when & then : 출품 가능한 1이 먼저, 나머지는 받은 순서 그대로다
    expect(selectRequests(evaluations, 'ACTIVE').map((item) => item.evaluationId))
      .toEqual([1, 3, 2])
  })

  it('종료 목록은 접수 최신순을 그대로 둔다', () => {
    const evaluations = [
      request({ evaluationId: 3, status: 'REJECTED' }),
      request({ evaluationId: 2, status: 'APPROVED', auctionStatus: 'ENDED' }),
      request({ evaluationId: 1, status: 'APPROVED' }),
    ]

    expect(selectRequests(evaluations, 'CLOSED').map((item) => item.evaluationId)).toEqual([3, 2])
    expect(countRequests(evaluations, 'ACTIVE')).toBe(1)
  })
})

import { describe, expect, it } from 'vitest'

import type { EvaluationSummary } from './types'
import { summarizeEvaluatorHome } from './evaluator-home-summary'

const assignment = (
  status: EvaluationSummary['status'],
): EvaluationSummary => ({
  evaluationId: 1,
  status,
  assigned: true,
  plateNumber: '12가3456',
  manufacturer: 'HYUNDAI',
  model: '쏘나타',
  modelYear: 2024,
  visitDate: '2026-08-13T10:00:00',
  visitAddress: '서울특별시 성동구',
  requestedAt: '2026-08-12T10:00:00',
})

describe('평가사 홈 업무 요약', () => {
  it('배정 대기 건수와 내 담당 상태를 서버 상태별로 집계한다', () => {
    const summary = summarizeEvaluatorHome(4, [
      assignment('REQUESTED'),
      assignment('REQUESTED'),
      assignment('APPROVED'),
      assignment('REJECTED'),
    ])

    expect(summary).toEqual({
      assignableCount: 4,
      assignmentCount: 4,
      pendingCount: 2,
      approvedCount: 1,
      rejectedCount: 1,
    })
  })

  it('업무가 없으면 모든 건수가 0이다', () => {
    expect(summarizeEvaluatorHome(0, [])).toEqual({
      assignableCount: 0,
      assignmentCount: 0,
      pendingCount: 0,
      approvedCount: 0,
      rejectedCount: 0,
    })
  })
})

import type { EvaluationSummary } from './types'

export interface EvaluatorHomeSummary {
  assignableCount: number
  assignmentCount: number
  pendingCount: number
  approvedCount: number
  rejectedCount: number
}

/** 서버의 평가 상태를 화면 전용 상태로 복제하지 않고 홈에서 필요한 건수만 파생한다. */
export function summarizeEvaluatorHome(
  assignableCount: number,
  assignments: EvaluationSummary[],
): EvaluatorHomeSummary {
  return assignments.reduce<EvaluatorHomeSummary>(
    (summary, assignment) => {
      if (assignment.status === 'REQUESTED') summary.pendingCount += 1
      if (assignment.status === 'APPROVED') summary.approvedCount += 1
      if (assignment.status === 'REJECTED') summary.rejectedCount += 1

      return summary
    },
    {
      assignableCount,
      assignmentCount: assignments.length,
      pendingCount: 0,
      approvedCount: 0,
      rejectedCount: 0,
    },
  )
}

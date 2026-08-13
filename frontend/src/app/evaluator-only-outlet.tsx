import { Navigate, Outlet } from 'react-router-dom'

import type { UserRole } from '@/types/domain'

/** 평가 업무 화면은 평가사만 통과시킨다. API도 같은 역할을 서버에서 다시 검사한다. */
export function EvaluatorOnlyOutlet({ role }: { role: UserRole | null }) {
  return role === 'EVALUATOR' ? <Outlet /> : <Navigate to="/" replace />
}
